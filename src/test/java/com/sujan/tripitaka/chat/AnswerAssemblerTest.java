package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.model.AnswerDraft;
import com.sujan.tripitaka.model.MentalModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerAssemblerTest {

    private final List<Document> retrieved = List.of(
            passage("c1", "mn10", "Majjhima Nikāya"),
            passage("c2", "dn22", "Dīgha Nikāya"),
            passage("c3", "mn118", "Majjhima Nikāya"));

    @Test
    void keepsOnlyCitationsFromThisRetrievalWithTrustedMetadata() {
        var draft = new AnswerDraft("The four foundations…", List.of("sati"),
                List.of("c1", "invented-id", "c1"), null, List.of());

        var response = AnswerAssembler.assemble("conv", "query", draft, retrieved);

        assertEquals(1, response.citations().size());
        assertEquals("mn10", response.citations().get(0).textId());
        assertTrue(response.unknowns().stream().anyMatch(u -> u.contains("removed")));
    }

    @Test
    void sourceChartComesFromRetrievalNotTheModel() {
        var draft = new AnswerDraft("answer", null, List.of("c2"), null, null);

        var response = AnswerAssembler.assemble("conv", "query", draft, retrieved);

        assertEquals(Map.of("Majjhima Nikāya", 2, "Dīgha Nikāya", 1), response.sourceDistribution());
    }

    @Test
    void rendersSanitizedMentalModel() {
        var model = new MentalModel("hierarchy", "Eightfold path",
                List.of(new MentalModel.Node("wisdom", "Wisdom"), new MentalModel.Node("view", "Right view")),
                List.of(new MentalModel.Edge("wisdom", "view", null)));
        var draft = new AnswerDraft("answer", null, List.of("c1"), model, null);

        var response = AnswerAssembler.assemble("conv", "query", draft, retrieved);

        assertNotNull(response.mermaid());
        assertTrue(response.mermaid().startsWith("flowchart LR"));
    }

    @Test
    void flagsUngroundedAnswerWhenNothingRetrieved() {
        var draft = new AnswerDraft("answer", null, List.of(), null, null);

        var response = AnswerAssembler.assemble("conv", "query", draft, List.of());

        assertTrue(response.unknowns().stream().anyMatch(u -> u.contains("not grounded")));
    }

    @Test
    void rejectsEmptyAnswer() {
        assertThrows(IllegalStateException.class, () ->
                AnswerAssembler.assemble("conv", "q", new AnswerDraft(" ", null, null, null, null), retrieved));
    }

    private static Document passage(String id, String textId, String nikaya) {
        return Document.builder().id(id).text("passage text for " + textId).metadata(Map.of(
                "textId", textId, "title", "Title " + textId, "nikaya", nikaya, "reference", textId + ":1.1")).build();
    }
}
