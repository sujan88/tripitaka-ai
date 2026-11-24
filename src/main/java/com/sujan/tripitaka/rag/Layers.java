package com.sujan.tripitaka.rag;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The layers of the canon a passage can come from, and how many of each an answer gets.
 * The suttas are the primary source; the Abhidhamma, the commentaries and the Visuddhimagga explain them.
 * Collections are named by layer: "sutta-si", "abhidhamma-si", "atthakatha-si", "visuddhimagga-si".
 */
public final class Layers {

    private Layers() {
    }

    /** Layer name the model sees for a collection, so it can say where each point comes from. */
    public static String of(String collection) {
        String c = collection == null ? "" : collection;
        if (c.startsWith("abhidhamma")) {
            return "Abhidhamma";
        }
        if (c.startsWith("atthakatha")) {
            return "Commentary (Aṭṭhakathā)";
        }
        if (c.startsWith("visuddhimagga")) {
            return "Visuddhimagga (Buddhaghosa's treatise)";
        }
        return "Sutta";
    }

    public static String of(Document document) {
        return of(String.valueOf(document.getMetadata().get("collection")));
    }

    /**
     * Final selection when several layers were searched: the top {@code primaryCount} primary (sutta) passages are
     * always kept, exactly as a suttas-only search would return them; up to {@code maxOthers} passages from the
     * other layers are added, but only if they score at least as high as the weakest sutta kept. A question about a
     * particular sutta therefore gets no Abhidhamma, while a question about how karma ripens does.
     *
     * @param reranked candidates sorted by relevance, highest first
     */
    public static List<Document> suttasPlusRelevantLayers(List<Document> reranked, String primary,
                                                         int primaryCount, int maxOthers) {
        List<Document> suttas = reranked.stream()
                .filter(d -> primary.equals(d.getMetadata().get("collection")))
                .limit(primaryCount)
                .toList();
        double floor = suttas.stream().mapToDouble(Layers::score).min().orElse(Double.NEGATIVE_INFINITY);
        List<Document> others = reranked.stream()
                .filter(d -> !primary.equals(d.getMetadata().get("collection")))
                .filter(d -> score(d) >= floor)
                .limit(maxOthers)
                .toList();
        var chosen = new java.util.HashSet<Document>(suttas);
        chosen.addAll(others);
        return reranked.stream().filter(chosen::contains).toList();
    }

    private static double score(Document document) {
        return document.getScore() == null ? 0.0 : document.getScore();
    }

    /**
     * Picks {@code total} passages from a ranked list while keeping at least {@code minPrimary} from the primary
     * collection (when that many were found), so commentary never crowds the Buddha's discourses out of an answer.
     * The result keeps the ranking order.
     */
    public static List<Document> withPrimaryQuota(List<Document> ranked, int total, String primary, int minPrimary) {
        List<Document> primaryDocs = ranked.stream()
                .filter(d -> primary.equals(d.getMetadata().get("collection")))
                .toList();
        Set<Document> chosen = new LinkedHashSet<>(primaryDocs.subList(0, Math.min(Math.min(minPrimary, total), primaryDocs.size())));
        for (Document document : ranked) {
            if (chosen.size() >= total) {
                break;
            }
            chosen.add(document);
        }
        List<Document> ordered = new ArrayList<>();
        for (Document document : ranked) {
            if (chosen.contains(document)) {
                ordered.add(document);
            }
        }
        return List.copyOf(ordered);
    }
}
