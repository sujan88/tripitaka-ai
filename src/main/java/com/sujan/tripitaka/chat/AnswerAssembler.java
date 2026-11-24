package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.ingest.PassageChunker;
import com.sujan.tripitaka.model.AnswerDraft;
import com.sujan.tripitaka.model.AskResponse;
import com.sujan.tripitaka.model.MentalModel;
import com.sujan.tripitaka.rag.Layers;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns an untrusted model draft into the API response. Pure function, unit-tested.
 * - citations must reference chunks retrieved in THIS request; their metadata comes from the index
 * - the mental model is sanitized before Mermaid is generated
 * - the source chart is computed from retrieval metadata, never from the model
 */
public final class  AnswerAssembler {

    private static final int EXCERPT_CHARS = 280;
    private static final int MAX_FOLLOW_UPS = 3;
    private static final int MAX_FOLLOW_UP_CHARS = 200;

    private AnswerAssembler() {
    }

    public static AskResponse assemble(String conversationId, String searchQuery,
                                       AnswerDraft draft, List<Document> retrieved) {
        if (draft == null || draft.answer() == null || draft.answer().isBlank()) {
            throw new IllegalStateException("Model returned no answer");
        }
        Map<String, Document> byId = retrieved.stream()
                .collect(Collectors.toMap(Document::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<String> unknowns = new ArrayList<>(nullSafe(draft.unknowns()));
        List<AskResponse.Citation> citations = new ArrayList<>();
        int invented = 0;
        for (String chunkId : new LinkedHashSet<>(nullSafe(draft.citedChunkIds()))) {
            Document document = byId.get(chunkId);
            if (document == null) {
                invented++;
                continue;
            }
            var metadata = document.getMetadata();
            citations.add(new AskResponse.Citation(
                    chunkId,
                    String.valueOf(metadata.get("textId")),
                    String.valueOf(metadata.get("title")),
                    String.valueOf(metadata.get("nikaya")),
                    String.valueOf(metadata.get("reference")),
                    excerpt(PassageChunker.withoutHeader(document.getText())),
                    Layers.of(document)));
        }
        if (invented > 0) {
            unknowns.add(invented + " citation(s) did not match any retrieved passage and were removed.");
        }
        if (retrieved.isEmpty()) {
            unknowns.add("No passages were retrieved; this answer is not grounded in the texts.");
        } else if (citations.isEmpty()) {
            unknowns.add("The answer cites no retrieved passage; treat it as unverified.");
        }

        MentalModel model = draft.mentalModel() == null ? null : draft.mentalModel().sanitized();
        if (model != null && model.isEmpty()) {
            model = null;
        }

        return new AskResponse(
                conversationId,
                searchQuery,
                draft.answer(),
                nullSafe(draft.keyConcepts()).stream().filter(c -> c != null && !c.isBlank()).limit(10).toList(),
                List.copyOf(citations),
                model,
                model == null ? null : model.toMermaid(),
                sourceDistribution(retrieved),
                List.copyOf(unknowns),
                followUps(draft.followUps()),
                AskResponse.GENERATED_BY_AI);
    }

    /** At most three distinct, non-blank, short questions. */
    static List<String> followUps(List<String> proposed) {
        return nullSafe(proposed).stream()
                .filter(q -> q != null && !q.isBlank())
                .map(String::trim)
                .filter(q -> q.length() <= MAX_FOLLOW_UP_CHARS)
                .distinct()
                .limit(MAX_FOLLOW_UPS)
                .toList();
    }

    static Map<String, Integer> sourceDistribution(List<Document> retrieved) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Document document : retrieved) {
            counts.merge(String.valueOf(document.getMetadata().getOrDefault("nikaya", "Other")), 1, Integer::sum);
        }
        return counts;
    }

    private static String excerpt(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= EXCERPT_CHARS ? text : text.substring(0, EXCERPT_CHARS).trim() + "…";
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
