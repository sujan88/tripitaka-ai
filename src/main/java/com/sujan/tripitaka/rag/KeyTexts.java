package com.sujan.tripitaka.rag;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A lesson's key text (e.g. MN 135 for "Why are people born different?") must be in its answer even when other
 * suttas rank higher: the lesson is about that text. Pure functions, unit-tested.
 */
public final class KeyTexts {

    private KeyTexts() {
    }

    static boolean isKey(Document document, Set<String> keyTextIds) {
        return keyTextIds.contains(String.valueOf(document.getMetadata().get("textId")));
    }

    /**
     * Makes sure at least {@code min} of the chosen passages come from the key texts (when that many were ranked):
     * the best-ranked key-text passages replace the lowest-ranked other passages. The result keeps the ranking
     * order and the size of {@code chosen}.
     *
     * @param chosen the passages picked so far, a subset of {@code ranked}
     * @param ranked every candidate, most relevant first
     */
    public static List<Document> ensure(List<Document> chosen, List<Document> ranked, Set<String> keyTextIds, int min) {
        if (keyTextIds.isEmpty() || chosen.isEmpty()) {
            return chosen;
        }
        long present = chosen.stream().filter(d -> isKey(d, keyTextIds)).count();
        if (present >= min) {
            return chosen;
        }
        Set<String> chosenIds = new HashSet<>(chosen.stream().map(Document::getId).toList());
        List<Document> additions = ranked.stream()
                .filter(d -> isKey(d, keyTextIds) && !chosenIds.contains(d.getId()))
                .limit(min - present)
                .toList();
        if (additions.isEmpty()) {
            return chosen;
        }
        // Drop the lowest-ranked passages that are not from a key text, one per addition.
        List<Document> kept = new ArrayList<>(chosen);
        int toDrop = Math.min(additions.size(), (int) (chosen.size() - present));
        for (int i = kept.size() - 1; i >= 0 && toDrop > 0; i--) {
            if (!isKey(kept.get(i), keyTextIds)) {
                kept.remove(i);
                toDrop--;
            }
        }
        Set<String> finalIds = new HashSet<>(kept.stream().map(Document::getId).toList());
        additions.stream().limit(chosen.size() - kept.size()).forEach(d -> finalIds.add(d.getId()));
        return ranked.stream().filter(d -> finalIds.contains(d.getId())).toList();
    }

    /** Appends candidates not already present (by id), keeping the existing order first. */
    static List<Document> appendNew(List<Document> base, List<Document> extra) {
        Set<String> ids = new HashSet<>(base.stream().map(Document::getId).toList());
        List<Document> all = new ArrayList<>(base);
        extra.stream().filter(d -> ids.add(d.getId())).forEach(all::add);
        return all;
    }
}
