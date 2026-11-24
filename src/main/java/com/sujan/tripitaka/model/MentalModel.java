package com.sujan.tripitaka.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Concept map the model proposes; the server sanitizes it and renders Mermaid.
 * Returned as data (nodes/edges) so a future mobile client can draw it natively.
 *
 * @param type  "flow", "cycle" or "hierarchy"
 */
public record MentalModel(String type, String title, List<Node> nodes, List<Edge> edges) {

    public static final int MAX_NODES = 14;
    private static final int MAX_DETAIL_CHARS = 360;

    /** @param detail plain-language explanation shown beside the diagram; never placed in Mermaid */
    public record Node(String id, String label, String detail) {
        public Node(String id, String label) {
            this(id, label, null);
        }
    }

    public record Edge(String from, String to, String label) {
    }

    /** Drops malformed nodes, caps size, and removes edges that point at unknown nodes. */
    public MentalModel sanitized() {
        List<Node> cleanNodes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Node node : nodes == null ? List.<Node>of() : nodes) {
            if (node == null || blank(node.id()) || blank(node.label()) || cleanNodes.size() >= MAX_NODES) {
                continue;
            }
            String id = safeId(node.id());
            String label = safeLabel(node.label());
            if (!label.isEmpty() && ids.add(id)) {
                cleanNodes.add(new Node(id, label, safeDetail(node.detail())));
            }
        }

        List<Edge> cleanEdges = new ArrayList<>();
        for (Edge edge : edges == null ? List.<Edge>of() : edges) {
            if (edge == null || blank(edge.from()) || blank(edge.to())) {
                continue;
            }
            String from = safeId(edge.from());
            String to = safeId(edge.to());
            if (ids.contains(from) && ids.contains(to)) {
                String label = edge.label() == null ? "" : safeLabel(edge.label());
                cleanEdges.add(new Edge(from, to, label.isEmpty() ? null : label));
            }
        }

        String cleanType = switch (type == null ? "" : type.trim().toLowerCase()) {
            case "cycle", "hierarchy" -> type.trim().toLowerCase();
            default -> "flow";
        };
        return new MentalModel(cleanType, blank(title) ? null : safeLabel(title), cleanNodes, cleanEdges);
    }

    public boolean isEmpty() {
        return nodes == null || nodes.isEmpty();
    }

    /** Mermaid source for the web UI. Call on a sanitized model. */
    public String toMermaid() {
        if (isEmpty()) {
            return null;
        }
        // Grow along the page's scrolling axis so large text stays readable: siblings stack vertically.
        var out = new StringBuilder("hierarchy".equals(type) ? "flowchart LR\n" : "flowchart TD\n");
        for (Node node : nodes) {
            out.append("  ").append(node.id()).append("[\"").append(node.label()).append("\"]\n");
        }
        for (Edge edge : edges) {
            out.append("  ").append(edge.from());
            out.append(edge.label() == null ? " --> " : " -- \"" + edge.label() + "\" --> ");
            out.append(edge.to()).append('\n');
        }
        return out.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeId(String raw) {
        String id = raw.trim().replaceAll("[^A-Za-z0-9_]", "_");
        // "end" closes a Mermaid block, so a node with that id breaks the whole diagram.
        return Character.isLetter(id.charAt(0)) && !id.equalsIgnoreCase("end") ? id : "n_" + id;
    }

    private static String safeDetail(String raw) {
        if (blank(raw)) {
            return null;
        }
        String detail = raw.replaceAll("\\s+", " ").trim();
        return detail.length() > MAX_DETAIL_CHARS ? detail.substring(0, MAX_DETAIL_CHARS - 1).trim() + "…" : detail;
    }

    /** Mermaid breaks on quotes, brackets and newlines; keep labels short and plain. */
    private static String safeLabel(String raw) {
        String label = raw.replaceAll("[\"\\[\\]{}<>|`\\r\\n]", " ").replaceAll("\\s+", " ").trim();
        return label.length() > 60 ? label.substring(0, 57) + "..." : label;
    }
}
