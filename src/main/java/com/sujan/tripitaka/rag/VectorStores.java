package com.sujan.tripitaka.rag;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One Qdrant collection per logical collection: "sutta-en" lives in "tripitaka_sutta_en", "sutta-si" in
 * "tripitaka_sutta_si". Languages never share an index, so each can be rebuilt, backed up or deleted on its own,
 * and a collection can later move to a different embedding model (vector size) without touching the others.
 */
@Component
public class VectorStores {

    private final QdrantClient client;
    private final EmbeddingModel embeddingModel;
    private final Map<String, VectorStore> stores = new ConcurrentHashMap<>();

    public VectorStores(QdrantClient client, EmbeddingModel embeddingModel) {
        this.client = client;
        this.embeddingModel = embeddingModel;
    }

    /** The store for a logical collection; its Qdrant collection is created on first use if missing. */
    public VectorStore forCollection(String collection) {
        return stores.computeIfAbsent(collection, c -> create(qdrantCollectionName(c)));
    }

    public static String qdrantCollectionName(String collection) {
        return "tripitaka_" + collection.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    private VectorStore create(String qdrantCollection) {
        var store = QdrantVectorStore.builder(client, embeddingModel)
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
