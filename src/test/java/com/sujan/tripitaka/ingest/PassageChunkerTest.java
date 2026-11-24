package com.sujan.tripitaka.ingest;

import com.sujan.tripitaka.model.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassageChunkerTest {

    private final PassageChunker chunker = new PassageChunker(300);

    @Test
    void packsWholePassagesAndKeepsReferenceRange() {
        var text = text(List.of(
                new SourceText.Passage("mn10:1", "a".repeat(120)),
                new SourceText.Passage("mn10:2", "b".repeat(120)),
                new SourceText.Passage("mn10:3", "c".repeat(120))));

        var chunks = chunker.chunk("sutta-en", "r1", text);

        assertEquals(2, chunks.size());
        assertEquals("mn10:1 … mn10:2", chunks.get(0).getMetadata().get("reference"));
        assertEquals("mn10:3", chunks.get(1).getMetadata().get("reference"));
    }

    @Test
    void searchHeaderStartsEveryChunkAndIsStrippedForReaders() {
        var text = new SourceText("kn-khp-5", "මඞ්ගල සූත්‍රය", "ඛුද්දක නිකාය", "kn-khp.json", List.of(
                new SourceText.Passage("p.1", "a".repeat(200)),
                new SourceText.Passage("p.2", "b".repeat(200))), "මඞ්ගල සූත්‍රය · මඞ්ගලසුත්තං");

        var chunks = chunker.chunk("sutta-si", "r1", text);

        assertEquals(2, chunks.size());
        chunks.forEach(chunk -> assertTrue(chunk.getText().startsWith("⟨මඞ්ගල සූත්‍රය · මඞ්ගලසුත්තං⟩")));
        assertEquals("a".repeat(200), PassageChunker.withoutHeader(chunks.get(0).getText()));
        assertEquals("plain text", PassageChunker.withoutHeader("plain text"));
    }

    @Test
    void splitsOversizedPassageWithinLimit() {
        var longPage = "This is a sentence. ".repeat(60);
        var chunks = chunker.chunk("pdf", "r1", text(List.of(new SourceText.Passage("p.7", longPage))));

        assertTrue(chunks.size() > 1);
        chunks.forEach(chunk -> assertTrue(chunk.getText().length() <= 301, chunk.getText().length() + " chars"));
        assertTrue(chunks.get(0).getMetadata().get("reference").toString().startsWith("p.7"));
    }

    @Test
    void chunkIdsAreStablePerReleaseAndDifferAcrossReleases() {
        var text = text(List.of(new SourceText.Passage("mn10:1", "teaching")));

        String first = chunker.chunk("sutta-en", "r1", text).get(0).getId();
        String again = chunker.chunk("sutta-en", "r1", text).get(0).getId();
        String other = chunker.chunk("sutta-en", "r2", text).get(0).getId();

        assertEquals(first, again);
        assertNotEquals(first, other);
    }

    private static SourceText text(List<SourceText.Passage> passages) {
        return new SourceText("mn10", "Mindfulness Meditation", "Majjhima Nikāya", "mn10.json", passages);
    }
}
