package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryEmbeddingsTest {

    @Test
    void preparedQueriesAreServedWithoutAnotherCall() {
        var model = new TripitakaRetrieverTest.CountingEmbeddingModel();
        var embeddings = new QueryEmbeddings(model);

        embeddings.prepare(List.of("a", "bb", "a"));
        float[] vector = embeddings.embed("bb");

        assertEquals(1, model.calls.get());
        assertArrayEquals(model.embed("bb"), vector);
    }

    @Test
    void unpreparedQueryFallsBackToTheModel() {
        var model = new TripitakaRetrieverTest.CountingEmbeddingModel();
        var embeddings = new QueryEmbeddings(model);

        embeddings.embed("not prepared");

        assertEquals(1, model.calls.get());
    }
}
