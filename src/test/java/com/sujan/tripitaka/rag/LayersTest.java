package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayersTest {

    private static Document doc(String id, String collection) {
        return Document.builder().id(id).text("t").metadata(Map.of("collection", collection)).build();
    }

    private static List<String> ids(List<Document> documents) {
        return documents.stream().map(Document::getId).toList();
    }

    @Test
    void namesEachLayer() {
        assertEquals("Sutta", Layers.of("sutta-si"));
        assertEquals("Abhidhamma", Layers.of("abhidhamma-si"));
        assertEquals("Commentary (Aṭṭhakathā)", Layers.of("atthakatha-si"));
        assertEquals("Visuddhimagga (Buddhaghosa's treatise)", Layers.of("visuddhimagga-si"));
        assertEquals("Sutta", Layers.of("sutta-en"));
    }

    @Test
    void keepsMinimumSuttasEvenWhenCommentaryRanksHigher() {
        var ranked = List.of(doc("c1", "atthakatha-si"), doc("a1", "abhidhamma-si"), doc("c2", "atthakatha-si"),
                doc("v1", "visuddhimagga-si"), doc("s1", "sutta-si"), doc("s2", "sutta-si"), doc("s3", "sutta-si"));

        var picked = Layers.withPrimaryQuota(ranked, 4, "sutta-si", 2);

        // Two suttas guaranteed, the other two slots by rank, original order kept.
        assertEquals(List.of("c1", "a1", "s1", "s2"), ids(picked));
    }

    private static Document scored(String id, String collection, double score) {
        return Document.builder().id(id).text("t").score(score).metadata(Map.of("collection", collection)).build();
    }

    @Test
    void deepQuestionKeepsSuttasAndAddsHigherScoringLayers() {
        // Commentary outranks every sutta: all suttas stay, and the relevant commentary is added on top.
        var reranked = List.of(scored("c1", "atthakatha-si", 0.9), scored("v1", "visuddhimagga-si", 0.8),
                scored("s1", "sutta-si", 0.6), scored("a1", "abhidhamma-si", 0.55), scored("s2", "sutta-si", 0.5),
                scored("c2", "atthakatha-si", 0.1));

        var picked = Layers.suttasPlusRelevantLayers(reranked, "sutta-si", 2, 3);

        assertEquals(List.of("c1", "v1", "s1", "a1", "s2"), ids(picked));
    }

    @Test
    void suttaQuestionGetsNoLayerThatScoresBelowItsSuttas() {
        var reranked = List.of(scored("s1", "sutta-si", 0.9), scored("s2", "sutta-si", 0.7),
                scored("a1", "abhidhamma-si", 0.3), scored("c1", "atthakatha-si", 0.2));

        assertEquals(List.of("s1", "s2"), ids(Layers.suttasPlusRelevantLayers(reranked, "sutta-si", 2, 3)));
    }

    @Test
    void capsTheNumberOfLayerPassages() {
        var reranked = List.of(scored("c1", "atthakatha-si", 0.9), scored("c2", "atthakatha-si", 0.85),
                scored("c3", "atthakatha-si", 0.8), scored("s1", "sutta-si", 0.5));

        assertEquals(List.of("c1", "c2", "s1"), ids(Layers.suttasPlusRelevantLayers(reranked, "sutta-si", 6, 2)));
    }

    @Test
    void takesWhatIsAvailableWhenFewSuttasWereFound() {
        var ranked = List.of(doc("c1", "atthakatha-si"), doc("s1", "sutta-si"), doc("a1", "abhidhamma-si"));

        assertEquals(List.of("c1", "s1", "a1"), ids(Layers.withPrimaryQuota(ranked, 5, "sutta-si", 3)));
    }

    @Test
    void singleCollectionIsJustTheTopOfTheRanking() {
        var ranked = List.of(doc("s1", "sutta-en"), doc("s2", "sutta-en"), doc("s3", "sutta-en"));

        assertEquals(List.of("s1", "s2"), ids(Layers.withPrimaryQuota(ranked, 2, "sutta-en", 3)));
    }
}
