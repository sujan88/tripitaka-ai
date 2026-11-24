package com.sujan.tripitaka.model;

import java.util.List;

/**
 * What the LLM must return (structured output). Untrusted until validated by AnswerAssembler.
 *
 * @param answer         detailed explanation in Markdown
 * @param keyConcepts    short Pali/English terms the answer relies on
 * @param citedChunkIds  CHUNK_IDs of passages actually used; must come from this request
 * @param mentalModel    concept map of the explanation
 * @param unknowns       what the passages do not settle
 * @param followUps      up to 3 short questions a learner might ask next
 */
public record AnswerDraft(String answer,
                          List<String> keyConcepts,
                          List<String> citedChunkIds,
                          MentalModel mentalModel,
                          List<String> unknowns,
                          List<String> followUps) {

    public AnswerDraft(String answer, List<String> keyConcepts, List<String> citedChunkIds,
                       MentalModel mentalModel, List<String> unknowns) {
        this(answer, keyConcepts, citedChunkIds, mentalModel, unknowns, null);
    }
}
