package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextExpanderTest {

    private static Document chunk(String textId, int index) {
        return Document.builder().id(textId + "-" + index).text("x".repeat(100))
                .metadata(Map.of("textId", textId, "chunkIndex", index)).build();
    }

    private static List<Document> text(String textId, int count) {
        var chunks = new ArrayList<Document>();
        for (int i = 0; i < count; i++) {
            chunks.add(chunk(textId, i));
        }
        return chunks;
    }

    private static List<String> ids(List<Document> documents) {
        return documents.stream().map(Document::getId).toList();
    }

    @Test
    void addsNeighboursInReadingOrderAndMarksRoles() {
        var result = ContextExpander.expand(List.of(chunk("mn10", 2)), hit -> text(String.valueOf(hit.getMetadata().get("textId")), 5), 1, 10_000);

        assertEquals(List.of("mn10-1", "mn10-2", "mn10-3"), ids(result));
        assertEquals(ContextExpander.MATCH, result.get(1).getMetadata().get(ContextExpander.ROLE));
        assertEquals(ContextExpander.CONTEXT, result.get(0).getMetadata().get(ContextExpander.ROLE));
    }

    @Test
    void keepsHitsButStopsAddingNeighboursAtBudget() {
        // Hits use 200 of 300 chars, so only one 100-char neighbour fits.
        var result = ContextExpander.expand(List.of(chunk("mn10", 2), chunk("sn56.11", 0)),
                hit -> text(String.valueOf(hit.getMetadata().get("textId")), 5), 2, 300);

        assertEquals(List.of("mn10-1", "mn10-2", "sn56.11-0"), ids(result));
    }

    @Test
    void groupsByBestRankedTextAndSkipsMissingEdges() {
        var hits = List.of(chunk("sn56.11", 0), chunk("mn10", 4), chunk("sn56.11", 3));
        var result = ContextExpander.expand(hits, hit -> text(String.valueOf(hit.getMetadata().get("textId")), 5), 1, 10_000);

        assertEquals(List.of("sn56.11-0", "sn56.11-1", "sn56.11-2", "sn56.11-3", "sn56.11-4", "mn10-3", "mn10-4"),
                ids(result));
    }

    @Test
    void windowZeroReturnsHitsOnly() {
        var result = ContextExpander.expand(List.of(chunk("mn10", 2)), hit -> text(String.valueOf(hit.getMetadata().get("textId")), 5), 0, 10_000);
        assertEquals(List.of("mn10-2"), ids(result));
    }
}
