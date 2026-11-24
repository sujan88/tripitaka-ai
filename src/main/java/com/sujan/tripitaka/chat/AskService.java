package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.model.AnswerDraft;
import com.sujan.tripitaka.model.AskRequest;
import com.sujan.tripitaka.model.AskResponse;
import com.sujan.tripitaka.rag.ContextExpander;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One question: rewrite follow-up -> hybrid retrieval -> structured answer -> validation.
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);
    private static final int HISTORY_MESSAGES = 6;
    private static final int HISTORY_CHARS = 600;

    private final ChatClient answerClient;
    private final ChatClient rewriteClient;
    private final ChatMemory chatMemory;
    private final TripitakaRetriever retriever;
    private final String collection;
    private final String sinhalaCollection;
    private final String answerSystemPrompt;
    private final String sinhalaInstructions;
    private final String rewriteSystemPrompt;

    public AskService(@Qualifier("answerClient") ChatClient answerClient,
                      @Qualifier("rewriteClient") ChatClient rewriteClient,
                      ChatMemory chatMemory,
                      TripitakaRetriever retriever,
                      @Value("${rag.collection}") String collection,
                      @Value("${rag.sinhala.collection}") String sinhalaCollection,
                      @Value("classpath:prompts/answer-system.md") Resource answerPrompt,
                      @Value("classpath:prompts/answer-language-si.md") Resource sinhalaPrompt,
                      @Value("classpath:prompts/rewrite-system.md") Resource rewritePrompt) throws IOException {
        this.answerClient = answerClient;
        this.rewriteClient = rewriteClient;
        this.chatMemory = chatMemory;
        this.retriever = retriever;
        this.collection = collection;
        this.sinhalaCollection = sinhalaCollection;
        this.answerSystemPrompt = answerPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.sinhalaInstructions = sinhalaPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.rewriteSystemPrompt = rewritePrompt.getContentAsString(StandardCharsets.UTF_8);
    }

    public AskResponse ask(AskRequest request) {
        long started = System.nanoTime();
        String conversationId = request.conversationId() == null
                ? UUID.randomUUID().toString() : request.conversationId();
        // English stays the default; Sinhala reads its own collection and answers in Sinhala.
        String targetCollection = request.sinhala() ? sinhalaCollection : collection;

        String searchQuery = standaloneQuery(conversationId, request.question());
        List<Document> passages = retriever.withContext(retriever.search(searchQuery, targetCollection));

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

    /** "Explain the third one" means nothing to a retriever; turn follow-ups into standalone queries. */
    String standaloneQuery(String conversationId, String question) {
        List<Message> history = chatMemory.get(conversationId);
        if (history.isEmpty()) {
            return question;
        }
        String transcript = history.subList(Math.max(0, history.size() - HISTORY_MESSAGES), history.size()).stream()
                .map(m -> m.getMessageType().name() + ": " + truncate(m.getText()))
                .collect(Collectors.joining("\n"));
        try {
            String rewritten = rewriteClient.prompt()
                    .system(rewriteSystemPrompt)
                    .user("CONVERSATION (untrusted data):\n" + transcript + "\n\nLATEST QUESTION:\n" + question)
                    .call()
                    .content();
            if (rewritten == null || rewritten.isBlank() || rewritten.length() > 300) {
                return question;
            }
            log.info("Rewrote follow-up question query='{}'", rewritten.trim());
            return rewritten.trim();
        } catch (RuntimeException failure) {
            log.warn("Query rewrite failed; using original question errorType={}", failure.getClass().getSimpleName());
            return question;
        }
    }

    private static String formatPassages(List<Document> passages) {
        if (passages.isEmpty()) {
            return "No passages were retrieved.";
        }
        return passages.stream().map(d -> """
                CHUNK_ID: %s
                TEXT: %s — %s (%s)
                REFERENCE: %s
                ROLE: %s
                CONTENT:
                %s
                """.formatted(d.getId(), d.getMetadata().get("textId"), d.getMetadata().get("title"),
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
