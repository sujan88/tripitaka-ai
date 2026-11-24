package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyTextsTest {

    private static Document doc(String id, String textId) {
        return Document.builder().id(id).text("t").metadata(Map.of("textId", textId)).build();
    }

    private static List<String> ids(List<Document> documents) {
        return documents.stream().map(Document::getId).toList();
    }

    private final Document a = doc("a", "an6.63"), b = doc("b", "mn136"), c = doc("c", "an3.61"),
            k1 = doc("k1", "mn135"), k2 = doc("k2", "mn135"), k3 = doc("k3", "mn135");

    @Test
    void addsTheKeyTextInPlaceOfTheLowestRankedPassages() {
        var ranked = List.of(a, b, k1, c, k2, k3);
        var chosen = List.of(a, b, c);

        var result = KeyTexts.ensure(chosen, ranked, Set.of("mn135"), 2);

        assertEquals(List.of("a", "k1", "k2"), ids(result)); // b and c dropped, ranking order kept
    }

    @Test
    void leavesTheChoiceAloneWhenTheKeyTextIsAlreadyThere() {
        var ranked = List.of(k1, a, k2, b);
        var chosen = List.of(k1, a, k2);

        assertEquals(chosen, KeyTexts.ensure(chosen, ranked, Set.of("mn135"), 2));
    }

    @Test
    void topsUpWhenOnlyOneKeyPassageWasChosen() {
        var ranked = List.of(a, k1, b, c, k2);
        var chosen = List.of(a, k1, b, c);

        assertEquals(List.of("a", "k1", "b", "k2"), ids(KeyTexts.ensure(chosen, ranked, Set.of("mn135"), 2)));
    }

    @Test
    void noKeyTextOrNoKeyCandidatesChangesNothing() {
        var chosen = List.of(a, b);

        assertEquals(chosen, KeyTexts.ensure(chosen, List.of(a, b, c), Set.of(), 2));
        assertEquals(chosen, KeyTexts.ensure(chosen, List.of(a, b, c), Set.of("mn135"), 2));
    }

    @Test
    void appendNewKeepsOrderAndSkipsDuplicates() {
        assertEquals(List.of("a", "k1", "b"), ids(KeyTexts.appendNew(List.of(a, k1), List.of(k1, b))));
    }
}
