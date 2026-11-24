package com.sujan.tripitaka.model;

import java.util.List;
import java.util.Map;

/**
 * @param searchQuery        standalone query actually used for retrieval (after follow-up rewrite)
 * @param sourceDistribution retrieved passages per nikaya; computed in code, never by the LLM
 * @param mermaid            ready-to-render diagram for the web UI (null when no model)
 */
public record AskResponse(String conversationId,
                          String searchQuery,
                          String answer,
                          List<String> keyConcepts,
                          List<Citation> citations,
                          MentalModel mentalModel,
                          String mermaid,
                          Map<String, Integer> sourceDistribution,
                          List<String> unknowns) {

    /** Built from trusted chunk metadata, not from model output. */
    public record Citation(String chunkId, String textId, String title, String nikaya,
                           String reference, String excerpt) {
    }
}
