package com.sujan.tripitaka.model;

import java.util.List;
import java.util.Map;

/**
 * @param searchQuery        standalone query actually used for retrieval (after follow-up rewrite)
 * @param sourceDistribution retrieved passages per nikaya; computed in code, never by the LLM
 * @param mermaid            ready-to-render diagram for the web UI (null when no model)
 * @param followUps          up to 3 questions to ask next, suggested by the AI
 * @param generatedBy        always "ai": the answer, mental model and follow-ups are written by a model (the
 *                           citations and their excerpts are the original texts)
 */
public record AskResponse(String conversationId,
                          String searchQuery,
                          String answer,
                          List<String> keyConcepts,
                          List<Citation> citations,
                          MentalModel mentalModel,
                          String mermaid,
                          Map<String, Integer> sourceDistribution,
                          List<String> unknowns,
                          List<String> followUps,
                          String generatedBy) {

    public static final String GENERATED_BY_AI = "ai";

    /**
     * Built from trusted chunk metadata, not from model output.
     *
     * @param layer "Sutta", "Abhidhamma", "Commentary (Aṭṭhakathā)" or "Visuddhimagga (Buddhaghosa's treatise)"
     */
    public record Citation(String chunkId, String textId, String title, String nikaya,
                           String reference, String excerpt, String layer) {
    }
}
