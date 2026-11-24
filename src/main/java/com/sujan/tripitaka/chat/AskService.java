package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.model.AnswerDraft;
import com.sujan.tripitaka.model.AskRequest;
import com.sujan.tripitaka.model.AskResponse;
import com.sujan.tripitaka.rag.ContextExpander;
import com.sujan.tripitaka.rag.Layers;
import com.sujan.tripitaka.rag.QueryExpander;
import com.sujan.tripitaka.rag.QueryExpander.QueryPlan;
import com.sujan.tripitaka.rag.TripitakaRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One question: plan searches (rewrite follow-up + expand, one call) -> hybrid retrieval -> structured answer -> validation.
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);
    private static final int HISTORY_MESSAGES = 6;
    private static final int HISTORY_CHARS = 600;

    private final ChatClient answerClient;
    private final ChatMemory chatMemory;
    private final TripitakaRetriever retriever;
    private final QueryExpander queryExpander;
    private final String collection;
    private final String sinhalaCollection;
    private final List<String> sinhalaLayers;
    private final String answerSystemPrompt;
    private final String sinhalaInstructions;

    public AskService(@Qualifier("answerClient") ChatClient answerClient,
                      ChatMemory chatMemory,
                      TripitakaRetriever retriever,
                      QueryExpander queryExpander,
                      @Value("${rag.collection}") String collection,
                      @Value("${rag.sinhala.collection}") String sinhalaCollection,
                      @Value("${rag.sinhala.layers:}") List<String> sinhalaLayers,
                      @Value("classpath:prompts/answer-system.md") Resource answerPrompt,
                      @Value("classpath:prompts/answer-language-si.md") Resource sinhalaPrompt) throws IOException {
        this.answerClient = answerClient;
        this.queryExpander = queryExpander;
        this.chatMemory = chatMemory;
        this.retriever = retriever;
        this.collection = collection;
        this.sinhalaCollection = sinhalaCollection;
        this.sinhalaLayers = sinhalaLayers.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        this.answerSystemPrompt = answerPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.sinhalaInstructions = sinhalaPrompt.getContentAsString(StandardCharsets.UTF_8);
    }

    public AskResponse ask(AskRequest request) {
        long started = System.nanoTime();
        String conversationId = request.conversationId() == null
                ? UUID.randomUUID().toString() : request.conversationId();
        // English stays the default. Sinhala searches its suttas first, plus (unless "suttas only") the
        // Abhidhamma, commentaries and Visuddhimagga; the suttas stay the primary source.
        List<String> targetCollections = new ArrayList<>(List.of(request.sinhala() ? sinhalaCollection : collection));
        if (request.sinhala() && !request.suttasOnly()) {
            targetCollections.addAll(sinhalaLayers);
        }

        // One model call turns the question (rewritten first if it is a follow-up) into the searches to run.
        QueryPlan plan = queryExpander.plan(request.question(), recentConversation(conversationId));
        String searchQuery = plan.standaloneQuery();
        List<Document> passages = retriever.withContext(retriever.searchQueries(plan.queries(), targetCollections));

        // Passages go in the system message so chat memory stores only the question, not the context.
        String system = answerSystemPrompt.replace("{{PASSAGES}}", formatPassages(passages))
                + (request.sinhala() ? "\n" + sinhalaInstructions : "");

        var result = answerClient.prompt()
                .system(system)
                .user(request.question())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                // Have the API enforce the schema; prompt-only JSON can come back malformed.
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .responseEntity(AnswerDraft.class);

        logUsage(result.getResponse() == null ? null : result.getResponse().getMetadata().getModel(),
                result.getResponse() == null ? null : result.getResponse().getMetadata().getUsage());
        AskResponse response = AnswerAssembler.assemble(conversationId, searchQuery, result.entity(), passages);

        log.info("Answered conversation={} passages={} citations={} durationMs={}",
                conversationId, passages.size(), response.citations().size(),
                (System.nanoTime() - started) / 1_000_000);
        return response;
    }

    /** The last few turns as text, so a follow-up ("explain the third one") can be made standalone; null if none. */
    String recentConversation(String conversationId) {
        List<Message> history = chatMemory.get(conversationId);
        if (history.isEmpty()) {
            return null;
        }
        return history.subList(Math.max(0, history.size() - HISTORY_MESSAGES), history.size()).stream()
                .map(m -> m.getMessageType().name() + ": " + truncate(m.getText()))
                .collect(Collectors.joining("\n"));
    }

    private static String formatPassages(List<Document> passages) {
        if (passages.isEmpty()) {
            return "No passages were retrieved.";
        }
        return passages.stream().map(d -> """
                CHUNK_ID: %s
                LAYER: %s
                TEXT: %s — %s (%s)
                REFERENCE: %s
                ROLE: %s
                CONTENT:
                %s
                """.formatted(d.getId(), Layers.of(d), d.getMetadata().get("textId"), d.getMetadata().get("title"),
                d.getMetadata().get("nikaya"), d.getMetadata().get("reference"),
                d.getMetadata().getOrDefault(ContextExpander.ROLE, ContextExpander.MATCH), d.getText()))
                .collect(Collectors.joining("\n---\n"));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= HISTORY_CHARS ? text : text.substring(0, HISTORY_CHARS) + "…";
    }

    private static void logUsage(String model, Usage usage) {
        if (usage != null) {
            log.info("Model usage model={} promptTokens={} completionTokens={} totalTokens={}",
                    model, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }
    }
}
