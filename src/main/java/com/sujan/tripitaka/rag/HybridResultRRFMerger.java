package com.sujan.tripitaka.rag;

import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reciprocal Rank Fusion of semantic and keyword results (unchanged from issue-investigator). */
public final class HybridResultRRFMerger {

    private static final int RANK_CONSTANT = 60;

    private HybridResultRRFMerger() {
    }

    public static List<Document> merge(List<Document> semanticResults, List<Document> keywordResults, int limit) {
        return mergeAll(List.of(semanticResults, keywordResults), limit);
    }

    /** RRF over any number of rankings, e.g. semantic + keyword results for each expanded query. */
    public static List<Document> mergeAll(List<List<Document>> rankings, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Map<String, Document> chunks = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();

        for (List<Document> ranking : rankings) {
            addRanks(ranking, chunks, scores);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(entry -> chunks.get(entry.getKey()).mutate().score(entry.getValue()).build())
                .toList();
    }

    private static void addRanks(List<Document> results, Map<String, Document> chunks, Map<String, Double> scores) {
        Set<String> seenInThisSearch = new HashSet<>();
        for (int index = 0; index < results.size(); index++) {
            Document chunk = results.get(index);
            // A duplicate within one search must not boost its score.
            if (!seenInThisSearch.add(chunk.getId())) {
                continue;
            }
            chunks.putIfAbsent(chunk.getId(), chunk);
            scores.merge(chunk.getId(), 1.0 / (RANK_CONSTANT + index + 1), Double::sum);
        }
    }
}
