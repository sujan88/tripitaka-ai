package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripitakaRetrieverTest {

    static final List<String> COLLECTIONS = List.of("sutta-si", "abhidhamma-si", "atthakatha-si", "visuddhimagga-si");
    static final List<String> QUERIES = List.of("කර්මය", "kamma vipāka", "කර්ම විපාක", "cetanā");

    /** Counts calls to the embedding API (call(), the only method that reaches the provider). */
    static class CountingEmbeddingModel implements EmbeddingModel {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            calls.incrementAndGet();
            var embeddings = new ArrayList<Embedding>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                String text = request.getInstructions().get(i);
                embeddings.add(new Embedding(new float[]{text.length(), text.hashCode()}, i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }
    }

    /** A store that embeds the query exactly like QdrantVectorStore does, then returns fixed hits. */
    static VectorStore fakeStore(String collection, EmbeddingModel embeddings) {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
            }

            @Override
            public void delete(List<String> ids) {
            }

            @Override
            public void delete(Filter.Expression expression) {
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                embeddings.embed(request.getQuery());
                int q = QUERIES.indexOf(request.getQuery());
                return List.of(hit(collection, "v" + q + "a"), hit(collection, "v" + ((q + 1) % 4) + "b"));
            }
        };
    }

    static Document hit(String collection, String suffix) {
        return Document.builder().id(collection + "-" + suffix).text("text " + suffix).score(0.5)
                .metadata(Map.of("collection", collection, "release", "r1", "textId", collection + "-t" + suffix))
                .build();
    }

    static TripitakaRetriever retriever(CountingEmbeddingModel embeddings) {
        return retriever(embeddings, 8);
    }

    static TripitakaRetriever retriever(CountingEmbeddingModel embeddings, int threads) {
        var stores = new VectorStores(embeddings, TripitakaRetrieverTest::fakeStore);
        var bm25 = mock(Bm25Store.class);
        when(bm25.search(anyString(), anyString(), anyString(), anyInt())).thenAnswer(call -> {
            String collection = call.getArgument(1);
            int q = QUERIES.indexOf((String) call.getArgument(0));
            return List.of(hit(collection, "k" + q), hit(collection, "v" + q + "a"));
        });
        var expander = mock(QueryExpander.class);
        when(expander.expand(anyString())).thenReturn(QUERIES);
        var releases = mock(CorpusReleaseStore.class);
        when(releases.current(anyString())).thenReturn(Optional.of("r1"));
        var reranker = new PassageReranker(RestClient.create(), false, "rerank-v3.5");
        return new TripitakaRetriever(stores, bm25, reranker, expander, releases, 25, 0.25, 6, 1, 30000,
                threads, 20000);
    }

    @Test
    void embedsAllQueriesOnceForEveryCollection() {
        var embeddings = new CountingEmbeddingModel();

        var found = retriever(embeddings).search(QUERIES.get(0), COLLECTIONS);

        assertFalse(found.isEmpty());
        // 4 queries x 4 collections searched; one batched embedding call.
        assertEquals(1, embeddings.calls.get());
    }

    @Test
    void repeatedQuestionNeedsNoNewEmbedding() {
        var embeddings = new CountingEmbeddingModel();
        var retriever = retriever(embeddings);

        retriever.search(QUERIES.get(0), COLLECTIONS);
        retriever.search(QUERIES.get(0), COLLECTIONS);

        assertEquals(1, embeddings.calls.get());
    }

    @Test
    void parallelSearchReturnsExactlyWhatSequentialSearchReturns() {
        for (int run = 0; run < 20; run++) {
            var sequential = retriever(new CountingEmbeddingModel(), 1).search(QUERIES.get(0), COLLECTIONS);
            var parallel = retriever(new CountingEmbeddingModel(), 8).search(QUERIES.get(0), COLLECTIONS);

            assertEquals(describe(sequential), describe(parallel));
        }
    }

    private static List<String> describe(List<Document> documents) {
        return documents.stream().map(d -> d.getId() + "@" + d.getScore() + d.getMetadata().get("retrievedBy")).toList();
    }
}
