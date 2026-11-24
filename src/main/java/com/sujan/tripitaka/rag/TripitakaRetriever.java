package com.sujan.tripitaka.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid retrieval: Qdrant similarity + Lucene BM25 -> RRF -> optional rerank.
 * Decoupled from any conversation/ownership logic, so it can be reused and evaluated on its own.
 */
@Service
public class TripitakaRetriever {

    private static final Logger log = LoggerFactory.getLogger(TripitakaRetriever.class);
    private static final int MAX_CHUNK_CHARS = 6000;
    /** With several layers, answers get a couple more passages, and at least this many from the suttas. */
    private static final int LAYERED_EXTRA = 2;
    private static final int MIN_PRIMARY = 3;

    private final VectorStores vectorStores;
    private final Bm25Store bm25Store;
    private final PassageReranker reranker;
    private final QueryExpander queryExpander;
    private final CorpusReleaseStore releases;
    private final int topK;
    private final double similarityThreshold;
    private final int answerChunks;
    private final int contextNeighbors;
    private final int contextMaxChars;

    public TripitakaRetriever(VectorStores vectorStores, Bm25Store bm25Store, PassageReranker reranker,
                              QueryExpander queryExpander, CorpusReleaseStore releases,
                              @Value("${rag.top-k:10}") int topK,
                              @Value("${rag.similarity-threshold:0.25}") double similarityThreshold,
                              @Value("${rag.answer-chunks:6}") int answerChunks,
                              @Value("${rag.context.neighbors:1}") int contextNeighbors,
                              @Value("${rag.context.max-chars:30000}") int contextMaxChars) {
        this.vectorStores = vectorStores;
        this.bm25Store = bm25Store;
        this.reranker = reranker;
        this.queryExpander = queryExpander;
        this.releases = releases;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.answerChunks = answerChunks;
        this.contextNeighbors = contextNeighbors;
        this.contextMaxChars = contextMaxChars;
    }

    /** Adds the chunks around each hit (same sutta, same release) so the model sees the wider teaching. */
    public List<Document> withContext(List<Document> hits) {
        if (hits.isEmpty() || contextNeighbors <= 0) {
            return ContextExpander.expand(hits, hit -> List.of(), 0, contextMaxChars);
        }
        // Each hit's neighbours come from its own collection and release (hits may span several layers).
        List<Document> expanded = ContextExpander.expand(hits, hit -> bm25Store.chunksOfText(
                        String.valueOf(hit.getMetadata().get("collection")),
                        String.valueOf(hit.getMetadata().get("release")),
                        String.valueOf(hit.getMetadata().get("textId"))),
                contextNeighbors, contextMaxChars);
        log.info("Context hits={} expanded={} chars={}", hits.size(), expanded.size(),
                expanded.stream().mapToInt(d -> d.getText().length()).sum());
        return expanded;
    }

    public List<Document> search(String query, String collection) {
        return search(query, List.of(collection));
    }

    /**
     * Searches several collections at once (e.g. suttas + Abhidhamma + commentaries). The first collection is the
     * primary source: when several are searched, at least {@link #MIN_PRIMARY} of the passages come from it.
     */
    public List<Document> search(String query, List<String> collections) {
        String primary = collections.get(0);
        Map<String, String> published = new java.util.LinkedHashMap<>();
        for (String collection : collections) {
            releases.current(collection).ifPresentOrElse(release -> published.put(collection, release),
                    () -> log.warn("No published release for collection={}; skipping it", collection));
        }
        if (published.isEmpty()) {
            return List.of();
        }

        // Every expanded query runs both searches in every collection; all rankings are fused, then reranked.
        List<String> queries = queryExpander.expand(query);
        List<List<Document>> rankings = new ArrayList<>();
        Set<String> semanticIds = new java.util.HashSet<>();
        Set<String> keywordIds = new java.util.HashSet<>();
        var filters = new FilterExpressionBuilder();
        for (var entry : published.entrySet()) {
            String collection = entry.getKey();
            String release = entry.getValue();
            var filter = filters.and(filters.eq("collection", collection), filters.eq("release", release)).build();
            for (String q : queries) {
                List<Document> semantic = usable(vectorStores.forCollection(collection).similaritySearch(SearchRequest.builder()
                        .query(q)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .filterExpression(filter)
                        .build()));
                List<Document> keyword = usable(bm25Store.search(q, collection, release, topK));
                rankings.add(semantic);
                rankings.add(keyword);
                semantic.forEach(d -> semanticIds.add(d.getId()));
                keyword.forEach(d -> keywordIds.add(d.getId()));
            }
        }

        boolean layered = published.size() > 1;
        List<Document> merged = HybridResultRRFMerger.mergeAll(rankings, topK * 2);
        // The reranker sees the question plus its expansions: a description ("the origin of the world") only
        // reaches the right sutta if the reranker also knows the name the expansion found ("Aggañña Sutta").
        String rerankQuery = String.join("\n", queries);
        int total = layered ? answerChunks + LAYERED_EXTRA : answerChunks;
        List<Document> reranked = reranker.rerank(rerankQuery, merged, layered ? total * 2 : total);
        List<Document> finalDocs = tagOrigins(semanticIds, keywordIds,
                layered ? Layers.withPrimaryQuota(reranked, total, primary, MIN_PRIMARY) : reranked);

        log.info("Retrieval collections={} queries={} semantic={} bm25={} merged={} final={} texts={}",
                published.keySet(), queries.size(), semanticIds.size(), keywordIds.size(), merged.size(), finalDocs.size(),
                finalDocs.stream().map(d -> String.valueOf(d.getMetadata().get("textId"))).toList());
        return finalDocs;
    }

    private static List<Document> tagOrigins(Set<String> semanticIds, Set<String> keywordIds, List<Document> docs) {
        return docs.stream().map(document -> {
            var origins = new ArrayList<String>();
            if (semanticIds.contains(document.getId())) {
                origins.add("semantic");
            }
            if (keywordIds.contains(document.getId())) {
                origins.add("bm25");
            }
            var metadata = new HashMap<>(document.getMetadata());
            metadata.put("retrievedBy", origins);
            return document.mutate().metadata(metadata).build();
        }).toList();
    }

    private static List<Document> usable(List<Document> documents) {
        List<Document> kept = documents.stream()
                .filter(d -> d.getText() != null && !d.getText().isBlank() && d.getText().length() <= MAX_CHUNK_CHARS)
                .toList();
        if (kept.size() < documents.size()) {
            log.warn("Dropped {} unusable chunks (empty or over {} chars)", documents.size() - kept.size(), MAX_CHUNK_CHARS);
        }
        return kept;
    }
}
