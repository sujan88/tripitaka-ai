package com.sujan.tripitaka.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Cohere rerank with RRF fallback (from issue-investigator). Disabled unless rag.rerank.enabled=true. */
@Service
public class PassageReranker {

    private static final Logger log = LoggerFactory.getLogger(PassageReranker.class);
    private static final int MAX_CANDIDATES = 70; // 50 sutta + 20 layer candidates; one Cohere search unit (≤100 docs)
    private static final int RATE_LIMIT_ATTEMPTS = 3;
    private static final long RATE_LIMIT_BACKOFF_MS = 3000;

    private final RestClient client;
    private final boolean enabled;
    private final String model;

    public PassageReranker(@Qualifier("rerankRestClient") RestClient client,
                           @Value("${rag.rerank.enabled:false}") boolean enabled,
                           @Value("${rag.rerank.model:rerank-v3.5}") String model) {
        this.client = client;
        this.enabled = enabled;
        this.model = model;
    }

    /**
     * Rate limits (HTTP 429, common on trial keys) are retried with a short backoff instead of silently
     * falling back to unranked results, which hurts answer quality without any visible error.
     */
    private RerankResponse callWithRetry(String question, List<Document> candidates, int count) {
        for (int attempt = 1; ; attempt++) {
            try {
                return client.post()
                        .uri("/rerank")
                        .body(Map.of(
                                "model", model,
                                "query", question,
                                "documents", candidates.stream().map(Document::getText).toList(),
                                "top_n", count,
                                "max_tokens_per_doc", 2048))
                        .retrieve()
                        .body(RerankResponse.class);
            } catch (HttpClientErrorException.TooManyRequests limited) {
                if (attempt >= RATE_LIMIT_ATTEMPTS) {
                    throw limited;
                }
                log.info("Rerank rate-limited; retrying attempt={} in {} ms", attempt + 1, RATE_LIMIT_BACKOFF_MS * attempt);
                try {
                    Thread.sleep(RATE_LIMIT_BACKOFF_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw limited;
                }
            }
        }
    }

    public List<Document> rerank(String question, List<Document> rrfResults, int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be positive");
        }
        var candidates = rrfResults.stream().limit(MAX_CANDIDATES).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        int count = Math.min(topK, candidates.size());
        if (!enabled || candidates.size() == 1) {
            return fallback(candidates, count, enabled ? "skipped" : "disabled");
        }

        try {
            var response = callWithRetry(question, candidates, count);

            if (response == null || response.results() == null || response.results().size() != count) {
                throw new IllegalArgumentException("Incomplete reranking response");
            }

            var ranked = new ArrayList<Document>();
            var seen = new HashSet<Integer>();
            for (Hit hit : response.results()) {
                if (hit == null || hit.index() == null || hit.index() < 0 || hit.index() >= candidates.size()
                        || !seen.add(hit.index()) || hit.relevanceScore() == null
                        || !Double.isFinite(hit.relevanceScore())) {
                    throw new IllegalArgumentException("Invalid reranking result");
                }
                Document original = candidates.get(hit.index());
                var metadata = new HashMap<>(original.getMetadata());
                metadata.put("rrfScore", original.getScore());
                metadata.put("scoreType", "RERANK");
                metadata.put("rerankStatus", "success");
                ranked.add(original.mutate().score(hit.relevanceScore()).metadata(metadata).build());
            }
            return ranked.stream().sorted(Comparator.comparing(Document::getScore).reversed()).toList();

        } catch (RestClientException | IllegalArgumentException failure) {
            log.warn("Reranking fallback errorType={}", failure.getClass().getSimpleName());
            return fallback(candidates, count, "fallback");
        }
    }

    private List<Document> fallback(List<Document> candidates, int count, String status) {
        return candidates.stream()
                .limit(count)
                .map(document -> {
                    var metadata = new HashMap<>(document.getMetadata());
                    metadata.put("scoreType", "RRF");
                    metadata.put("rerankStatus", status);
                    return document.mutate().metadata(metadata).build();
                })
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RerankResponse(List<Hit> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(Integer index, @JsonProperty("relevance_score") Double relevanceScore) {
    }
}
