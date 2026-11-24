package com.sujan.tripitaka.model;

import java.util.List;

/**
 * One canonical text (a sutta, or one PDF file) as passages in reading order.
 *
 * @param textId       stable id, e.g. "mn10"
 * @param title        human title, e.g. "Mindfulness Meditation"
 * @param nikaya       collection group, e.g. "Majjhima Nikāya"
 * @param source       where it came from (file name), for traceability
 * @param passages     paragraphs/pages with their own references
 * @param searchHeader optional names (titles, spellings, section) put at the top of every chunk so a search
 *                     for the sutta's name finds it; never shown to readers as scripture
 */
public record SourceText(String textId, String title, String nikaya, String source, List<Passage> passages,
                         String searchHeader) {

    public SourceText(String textId, String title, String nikaya, String source, List<Passage> passages) {
        this(textId, title, nikaya, source, passages, null);
    }

    /** @param reference segment range or page, e.g. "mn10:12.1" or "p.42" */
    public record Passage(String reference, String text) {
    }
}
