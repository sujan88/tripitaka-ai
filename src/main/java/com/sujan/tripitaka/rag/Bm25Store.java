package com.sujan.tripitaka.rag;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Lucene BM25 index, adapted from issue-investigator. Writes are staged per release:
 * clear -> add batches -> commit; searches always filter by collection and published release.
 */
@Service
public class Bm25Store {

    private static final List<String> STRING_FIELDS = List.of("collection", "release", "textId", "nikaya");
    private static final List<String> STORED_ONLY = List.of("title", "source", "reference");
    private static final int MAX_CHUNKS_PER_TEXT = 1000;

    private final IndexWriter writer;
    private final SearcherManager searchers;
    private final Analyzer analyzer;

    public Bm25Store(IndexWriter writer, SearcherManager searchers, Analyzer analyzer) {
        this.writer = writer;
        this.searchers = searchers;
        this.analyzer = analyzer;
    }

    /** Removes a partially staged copy of this release (e.g. from a crashed earlier run). */
    public void clearRelease(String collection, String release) throws IOException {
        writer.deleteDocuments(new Term("scope", scope(collection, release)));
    }

    public void add(List<Document> chunks) throws IOException {
        for (Document chunk : chunks) {
            var row = new org.apache.lucene.document.Document();
            row.add(new StringField("scope", scope(metadata(chunk, "collection"), metadata(chunk, "release")), Field.Store.NO));
            row.add(new StringField("chunkId", chunk.getId(), Field.Store.YES));
            row.add(new TextField("content", Objects.requireNonNull(chunk.getText()), Field.Store.YES));
            for (String key : STRING_FIELDS) {
                row.add(new StringField(key, metadata(chunk, key), Field.Store.YES));
            }
            for (String key : STORED_ONLY) {
                row.add(new StoredField(key, metadata(chunk, key)));
            }
            row.add(new StoredField("chunkIndex", metadata(chunk, "chunkIndex")));
            writer.addDocument(row);
        }
    }

    /** Removes every release of the collection except {@code keep}, then commits. */
    public void deleteOtherReleases(String collection, String keep) throws IOException {
        writer.deleteDocuments(new BooleanQuery.Builder()
                .add(new TermQuery(new Term("collection", collection)), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("release", keep)), BooleanClause.Occur.MUST_NOT)
                .build());
        commit();
    }

    public void commit() throws IOException {
        writer.commit();
        searchers.maybeRefreshBlocking();
    }

    public List<Document> search(String question, String collection, String release, int topK) {
        if (question == null || question.isBlank() || release == null) {
            return List.of();
        }
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("Invalid topK");
        }
        try {
            // escape() leaves the operators AND/OR/NOT intact; lowercase them so they are plain words.
            var textQuery = new QueryParser("content", analyzer)
                    .parse(QueryParser.escape(question.toLowerCase(Locale.ROOT)));
            var query = new BooleanQuery.Builder()
                    .add(textQuery, BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("collection", collection)), BooleanClause.Occur.FILTER)
                    .add(new TermQuery(new Term("release", release)), BooleanClause.Occur.FILTER)
                    .build();

            return run(query, topK);
        } catch (IOException | ParseException failure) {
            throw new IllegalStateException("BM25 search failed", failure);
        }
    }

    /** Every chunk of one text in a release, in reading order; used to add context around search hits. */
    public List<Document> chunksOfText(String collection, String release, String textId) {
        var query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term("textId", textId)), BooleanClause.Occur.FILTER)
                .add(new TermQuery(new Term("collection", collection)), BooleanClause.Occur.FILTER)
                .add(new TermQuery(new Term("release", release)), BooleanClause.Occur.FILTER)
                .build();
        try {
            return run(query, MAX_CHUNKS_PER_TEXT).stream()
                    .sorted(Comparator.comparingInt(d -> (Integer) d.getMetadata().get("chunkIndex")))
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException("BM25 text lookup failed", failure);
        }
    }

    private List<Document> run(Query query, int limit) throws IOException {
        var searcher = searchers.acquire();
        try {
            var results = new ArrayList<Document>();
            for (ScoreDoc hit : searcher.search(query, limit).scoreDocs) {
                var row = searcher.storedFields().document(hit.doc);
                Map<String, Object> metadata = new HashMap<>();
                for (String key : STRING_FIELDS) {
                    metadata.put(key, row.get(key));
                }
                for (String key : STORED_ONLY) {
                    metadata.put(key, row.get(key));
                }
                metadata.put("chunkIndex", Integer.parseInt(row.get("chunkIndex")));
                results.add(Document.builder()
                        .id(row.get("chunkId"))
                        .text(row.get("content"))
                        .score((double) hit.score)
                        .metadata(metadata)
                        .build());
            }
            return List.copyOf(results);
        } finally {
            searchers.release(searcher);
        }
    }

    private static String scope(String collection, String release) {
        return collection + "\u001f" + release;
    }

    private static String metadata(Document chunk, String key) {
        Object value = chunk.getMetadata().get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing chunk metadata: " + key);
        }
        return value.toString();
    }
}
