package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HybridResultRRFMergerTest {

    @Test
    void chunkFoundByBothRetrieversRanksFirst() {
        var semantic = List.of(doc("a"), doc("shared"));
        var keyword = List.of(doc("shared"), doc("b"));

        var merged = HybridResultRRFMerger.merge(semantic, keyword, 3);

        assertEquals("shared", merged.get(0).getId());
        assertEquals(3, merged.size());
    }

    @Test
    void duplicateWithinOneSearchDoesNotBoost() {
        var semantic = List.of(doc("a"), doc("a"), doc("a"));
        var keyword = List.of(doc("b"));

        var merged = HybridResultRRFMerger.merge(semantic, keyword, 2);

        assertEquals(merged.get(0).getScore(), merged.get(1).getScore(), 1e-9);
    }

    @Test
    void chunkFoundByMoreExpandedQueriesRanksHigher() {
        // Original question, then two expanded queries: "sutta" is found by all three, though never first.
        var rankings = List.of(
                List.of(doc("noise1"), doc("sutta")),
                List.of(doc("noise2"), doc("sutta")),
                List.of(doc("noise3"), doc("sutta")));

        var merged = HybridResultRRFMerger.mergeAll(rankings, 4);

        assertEquals("sutta", merged.get(0).getId());
        assertEquals(4, merged.size());
    }

    private static Document doc(String id) {
        return Document.builder().id(id).text("text " + id).build();
    }
}
