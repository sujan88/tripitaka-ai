package com.sujan.tripitaka.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The embedding model the vector stores search with. {@link #prepare} embeds all of a request's queries in one
 * batched call; each store's search then asks for {@code embed(query)} and gets the prepared vector instead of a
 * new API call. A query that was not prepared (or was evicted) is embedded as before, so nothing depends on the
 * cache being warm. Ingestion (document embedding) passes straight through.
 */
public class QueryEmbeddings implements EmbeddingModel {

    private static final int MAX_CACHED = 1024; // ~6 MB of 1536-d vectors

    private final EmbeddingModel delegate;
    private final Map<String, float[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > MAX_CACHED;
        }
    });

    public QueryEmbeddings(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    /** Embeds every query not already cached, in a single batched call (none if all are cached). */
    public void prepare(List<String> queries) {
        List<String> missing = new ArrayList<>(new LinkedHashSet<>(queries.stream()
                .filter(q -> q != null && !q.isBlank() && !cache.containsKey(q))
                .toList()));
        if (missing.isEmpty()) {
            return;
        }
        List<float[]> vectors = delegate.embed(missing);
        if (vectors == null || vectors.size() != missing.size()) {
            return; // stores fall back to embedding each query themselves
        }
        for (int i = 0; i < missing.size(); i++) {
            cache.put(missing.get(i), vectors.get(i));
        }
    }

    @Override
    public float[] embed(String text) {
        float[] cached = cache.get(text);
        return cached != null ? cached : delegate.embed(text);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate.call(request);
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(document);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return delegate.embed(texts);
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
        return delegate.embed(documents, options, batchingStrategy);
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return delegate.getEmbeddingContent(document);
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }
}
