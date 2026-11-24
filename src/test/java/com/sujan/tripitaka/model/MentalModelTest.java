package com.sujan.tripitaka.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MentalModelTest {

    @Test
    void dropsEdgesToUnknownNodesAndEscapesLabels() {
        var model = new MentalModel("CYCLE", "Dependent \"origination\"",
                List.of(new MentalModel.Node("ignorance", "Ignorance [avijjā]"),
                        new MentalModel.Node("1formations", "Formations")),
                List.of(new MentalModel.Edge("ignorance", "1formations", "conditions"),
                        new MentalModel.Edge("ignorance", "missing", null))).sanitized();

        assertEquals("cycle", model.type());
        assertEquals(1, model.edges().size());
        assertEquals("n_1formations", model.nodes().get(1).id());
        assertFalse(model.toMermaid().contains("[avijjā]"));
        assertTrue(model.toMermaid().contains("ignorance -- \"conditions\" --> n_1formations"));
    }

    @Test
    void renamesReservedIdAndDropsLabelsThatSanitizeToNothing() {
        var model = new MentalModel("flow", null,
                List.of(new MentalModel.Node("end", "Cessation"),
                        new MentalModel.Node("a", "Arising"),
                        new MentalModel.Node("b", "[]")),
                List.of(new MentalModel.Edge("a", "end", "[]"))).sanitized();

        assertEquals(List.of("n_end", "a"), model.nodes().stream().map(MentalModel.Node::id).toList());
        assertTrue(model.toMermaid().contains("a --> n_end"));
    }

    @Test
    void capsNodeCount() {
        var nodes = new ArrayList<MentalModel.Node>();
        for (int i = 0; i < 30; i++) {
            nodes.add(new MentalModel.Node("n" + i, "Node " + i));
        }
        var model = new MentalModel("flow", null, nodes, List.of()).sanitized();
        assertEquals(MentalModel.MAX_NODES, model.nodes().size());
    }

    @Test
    void unknownTypeFallsBackToFlow() {
        var model = new MentalModel("pie", null, List.of(new MentalModel.Node("a", "A")), null).sanitized();
        assertEquals("flow", model.type());
    }
}
