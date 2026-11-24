package com.sujan.tripitaka.ingest;

import java.util.Map;

/** Maps a SuttaCentral text id prefix to its nikaya, for metadata, filters and the source chart. */
final class Nikayas {

    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("dn", "Dīgha Nikāya"),
            Map.entry("mn", "Majjhima Nikāya"),
            Map.entry("sn", "Saṃyutta Nikāya"),
            Map.entry("an", "Aṅguttara Nikāya"),
            Map.entry("dhp", "Khuddaka Nikāya"),
            Map.entry("snp", "Khuddaka Nikāya"),
            Map.entry("ud", "Khuddaka Nikāya"),
            Map.entry("iti", "Khuddaka Nikāya"),
            Map.entry("thag", "Khuddaka Nikāya"),
            Map.entry("thig", "Khuddaka Nikāya"),
            Map.entry("kp", "Khuddaka Nikāya"));

    private Nikayas() {
    }

    static String of(String textId) {
        String prefix = textId.replaceAll("[^a-z].*$", "");
        return PREFIXES.getOrDefault(prefix, "Other");
    }
}
