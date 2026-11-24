package com.sujan.tripitaka.rag;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Small-to-big retrieval: search finds precise chunks, then the chunks around each hit are added
 * so the model reads the surrounding argument of the sutta, not an isolated excerpt.
 * Hits always stay; neighbours are added nearest-first until the character budget is used.
 * Pure function, unit-tested.
 */
public final class ContextExpander {

    public static final String ROLE = "contextRole";
    public static final String MATCH = "search match";
    public static final String CONTEXT = "surrounding context";

    private ContextExpander() {
    }

    /**
     * @param hits         ranked search results
     * @param chunksOfText all chunks of one text (by textId) in the same release
     * @param window       how many chunks to add on each side of a hit
     * @param maxChars     budget for neighbour text; hits are never dropped
     * @return hits plus neighbours, grouped by text (best-ranked text first) in reading order
     */
    public static List<Document> expand(List<Document> hits, Function<String, List<Document>> chunksOfText,
                                        int window, int maxChars) {
        Map<String, Document> chosen = new LinkedHashMap<>();
        Map<String, Integer> textRank = new HashMap<>();
        int used = 0;
        for (Document hit : hits) {
            chosen.put(hit.getId(), withRole(hit, MATCH));
            textRank.putIfAbsent(textId(hit), textRank.size());
            used += length(hit);
        }

        Map<String, Map<Integer, Document>> siblings = new HashMap<>();
        for (int distance = 1; distance <= window; distance++) {
            for (Document hit : hits) {
                Map<Integer, Document> byIndex = siblings.computeIfAbsent(textId(hit), id -> index(chunksOfText.apply(id)));
                for (int offset : new int[]{-distance, distance}) {
                    Document neighbour = byIndex.get(chunkIndex(hit) + offset);
                    if (neighbour == null || chosen.containsKey(neighbour.getId())
                            || used + length(neighbour) > maxChars) {
                        continue;
                    }
                    chosen.put(neighbour.getId(), withRole(neighbour, CONTEXT));
                    used += length(neighbour);
                }
            }
        }

        List<Document> ordered = new ArrayList<>(chosen.values());
        ordered.sort(Comparator.<Document>comparingInt(d -> textRank.getOrDefault(textId(d), Integer.MAX_VALUE))
                .thenComparingInt(ContextExpander::chunkIndex));
        return List.copyOf(ordered);
    }

    static int chunkIndex(Document document) {
        Object value = document.getMetadata().get("chunkIndex");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return (int) Double.parseDouble(String.valueOf(value));
    }

    private static Map<Integer, Document> index(List<Document> chunks) {
        Map<Integer, Document> byIndex = new HashMap<>();
        for (Document chunk : chunks == null ? List.<Document>of() : chunks) {
            byIndex.putIfAbsent(chunkIndex(chunk), chunk);
        }
        return byIndex;
    }

    private static String textId(Document document) {
        return String.valueOf(document.getMetadata().get("textId"));
    }

    private static int length(Document document) {
        return document.getText() == null ? 0 : document.getText().length();
    }

    private static Document withRole(Document document, String role) {
        var metadata = new HashMap<>(document.getMetadata());
        metadata.put(ROLE, role);
        return document.mutate().metadata(metadata).build();
    }
}
