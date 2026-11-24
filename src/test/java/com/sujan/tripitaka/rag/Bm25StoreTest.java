package com.sujan.tripitaka.rag;

import com.sujan.tripitaka.config.Bm25Config;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25StoreTest {

    @Test
    void paliTermMatchesWithOrWithoutDiacritics() throws Exception {
        try (var directory = new ByteBuffersDirectory();
             var analyzer = Bm25Config.paliFriendlyAnalyzer();
             var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
             var searchers = new SearcherManager(writer, new SearcherFactory())) {
            var store = new Bm25Store(writer, searchers, analyzer);
            store.add(List.of(chunk("c1", "r1", "The quenching of craving is Nibbāna.")));
            store.commit();

            var results = store.search("nibbana", "sutta-en", "r1", 5);

            assertEquals(List.of("c1"), results.stream().map(Document::getId).toList());
            assertEquals("mn1", results.get(0).getMetadata().get("textId"));
        }
    }

    @Test
    void searchesOnlyThePublishedRelease() throws Exception {
        try (var directory = new ByteBuffersDirectory();
             var analyzer = Bm25Config.paliFriendlyAnalyzer();
             var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
             var searchers = new SearcherManager(writer, new SearcherFactory())) {
            var store = new Bm25Store(writer, searchers, analyzer);
            store.add(List.of(chunk("old", "r1", "mindfulness of breathing")));
            store.add(List.of(chunk("new", "r2", "mindfulness of breathing")));
            store.commit();

            var results = store.search("mindfulness", "sutta-en", "r2", 5);

            assertEquals(List.of("new"), results.stream().map(Document::getId).toList());
        }
    }

    @Test
    void treatsUppercaseOperatorsAsPlainWords() throws Exception {
        try (var directory = new ByteBuffersDirectory();
             var analyzer = Bm25Config.paliFriendlyAnalyzer();
             var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
             var searchers = new SearcherManager(writer, new SearcherFactory())) {
            var store = new Bm25Store(writer, searchers, analyzer);
            store.add(List.of(chunk("c1", "r1", "form is not self")));
            store.commit();

            assertEquals(1, store.search("form AND", "sutta-en", "r1", 5).size());
            assertEquals(1, store.search("Is form NOT self OR", "sutta-en", "r1", 5).size());
        }
    }

    @Test
    void clearReleaseRemovesPartiallyStagedChunks() throws Exception {
        try (var directory = new ByteBuffersDirectory();
             var analyzer = Bm25Config.paliFriendlyAnalyzer();
             var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
             var searchers = new SearcherManager(writer, new SearcherFactory())) {
            var store = new Bm25Store(writer, searchers, analyzer);
            store.add(List.of(chunk("c1", "r1", "four noble truths")));
            store.commit();

            store.clearRelease("sutta-en", "r1");
            store.commit();

            assertTrue(store.search("truths", "sutta-en", "r1", 5).isEmpty());
        }
    }

    @Test
    void deleteOtherReleasesKeepsOnlyThePublishedOneOfThatCollection() throws Exception {
        try (var directory = new ByteBuffersDirectory();
             var analyzer = Bm25Config.paliFriendlyAnalyzer();
             var writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
             var searchers = new SearcherManager(writer, new SearcherFactory())) {
            var store = new Bm25Store(writer, searchers, analyzer);
            store.add(List.of(chunk("old", "r1", "noble truths"), chunk("new", "r2", "noble truths"),
                    chunk("sinhala", "s1", "noble truths", "sutta-si")));
            store.commit();

            store.deleteOtherReleases("sutta-en", "r2");

            assertTrue(store.search("truths", "sutta-en", "r1", 5).isEmpty());
            assertEquals(List.of("new"), store.search("truths", "sutta-en", "r2", 5).stream().map(Document::getId).toList());
            assertEquals(1, store.search("truths", "sutta-si", "s1", 5).size()); // other collections untouched
        }
    }

    private static Document chunk(String id, String release, String text) {
        return chunk(id, release, text, "sutta-en");
    }

    private static Document chunk(String id, String release, String text, String collection) {
        return Document.builder().id(id).text(text).metadata(Map.of(
                "collection", collection, "release", release, "textId", "mn1",
                "nikaya", "Majjhima Nikāya", "title", "The Root of All Things",
                "source", "mn1.json", "reference", "mn1:1.1", "chunkIndex", 0)).build();
    }
}
