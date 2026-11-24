package com.sujan.tripitaka.rag;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * One Qdrant collection per logical collection: "sutta-en" lives in "tripitaka_sutta_en", "sutta-si" in
 * "tripitaka_sutta_si". Languages never share an index, so each can be rebuilt, backed up or deleted on its own,
 * and a collection can later move to a different embedding model (vector size) without touching the others.
 * All stores embed through one {@link QueryEmbeddings}, so a request's queries are embedded once, not once per
 * collection.
 */
@Component
public class VectorStores {

    private final QueryEmbeddings embeddings;
    private final BiFunction<String, EmbeddingModel, VectorStore> factory;
    private final Map<String, VectorStore> stores = new ConcurrentHashMap<>();

    @Autowired
    public VectorStores(QdrantClient client, EmbeddingModel embeddingModel) {
        this(embeddingModel, (collection, embeddings) -> create(client, embeddings, qdrantCollectionName(collection)));
    }

    /** For tests: any store implementation, built over the shared query embeddings. */
    VectorStores(EmbeddingModel embeddingModel, BiFunction<String, EmbeddingModel, VectorStore> factory) {
        this.embeddings = new QueryEmbeddings(embeddingModel);
        this.factory = factory;
    }

    /** The store for a logical collection; its Qdrant collection is created on first use if missing. */
    public VectorStore forCollection(String collection) {
        return stores.computeIfAbsent(collection, c -> factory.apply(c, embeddings));
    }

    /** Embeds a request's queries in one batched call before they are searched in every collection. */
    public void prepareQueries(List<String> queries) {
        embeddings.prepare(queries);
    }

    public static String qdrantCollectionName(String collection) {
        return "tripitaka_" + collection.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    private static VectorStore create(QdrantClient client, EmbeddingModel embeddings, String qdrantCollection) {
        var store = QdrantVectorStore.builder(client, embeddings)
                .collectionName(qdrantCollection)
                .initializeSchema(true)
                .build();
        try {
            store.afterPropertiesSet();
        } catch (Exception failure) {
            throw new IllegalStateException("Could not open Qdrant collection " + qdrantCollection, failure);
        }
        return store;
    }
}
