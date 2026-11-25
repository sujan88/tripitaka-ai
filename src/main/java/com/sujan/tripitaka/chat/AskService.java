package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.curriculum.Curriculum.Lesson;
import com.sujan.tripitaka.curriculum.CurriculumNotFoundException;
import com.sujan.tripitaka.curriculum.CurriculumRepository;
import com.sujan.tripitaka.model.AnswerDraft;
import com.sujan.tripitaka.model.AskRequest;
import com.sujan.tripitaka.model.AskResponse;
import com.sujan.tripitaka.rag.ContextExpander;
import com.sujan.tripitaka.cost.CostMeter;
import com.sujan.tripitaka.cost.UsageGuard;
import com.sujan.tripitaka.rag.CorpusReleaseStore;
import com.sujan.tripitaka.rag.Layers;
import com.sujan.tripitaka.rag.QueryExpander;
import com.sujan.tripitaka.rag.QueryExpander.QueryPlan;
import com.sujan.tripitaka.rag.TripitakaRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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
import java.util.Map;
import java.util.Set;
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
    private final Map<String, String> depthInstructions;
    private final String lessonTemplate;
    private final CurriculumRepository curriculum;
    private final AnswerCache answerCache;
    private final CorpusReleaseStore releases;
    private final CostMeter costMeter;
    private final UsageGuard usageGuard;
    /** Changes whenever a prompt or model that shapes answers changes, so old cached answers stop matching. */
    private final String answerFingerprint;

    public AskService(@Qualifier("answerClient") ChatClient answerClient,
                      ChatMemory chatMemory,
                      TripitakaRetriever retriever,
                      QueryExpander queryExpander,
                      CurriculumRepository curriculum,
                      AnswerCache answerCache,
                      CorpusReleaseStore releases,
                      CostMeter costMeter,
                      UsageGuard usageGuard,
                      @Value("${rag.answer-model}") String answerModel,
                      @Value("${rag.query-expansion.model:gpt-5.4}") String expansionModel,
                      @Value("${rag.query-expansion.enabled:true}") boolean expansionEnabled,
                      @Value("${rag.collection}") String collection,
                      @Value("${rag.sinhala.collection}") String sinhalaCollection,
                      @Value("${rag.sinhala.layers:}") List<String> sinhalaLayers,
                      @Value("classpath:prompts/answer-system.md") Resource answerPrompt,
                      @Value("classpath:prompts/answer-language-si.md") Resource sinhalaPrompt,
                      @Value("classpath:prompts/answer-depth-simple.md") Resource simplePrompt,
                      @Value("classpath:prompts/answer-depth-deep.md") Resource deepPrompt,
                      @Value("classpath:prompts/answer-depth-practice.md") Resource practicePrompt,
                      @Value("classpath:prompts/answer-lesson.md") Resource lessonPrompt) throws IOException {
        this.answerClient = answerClient;
        this.queryExpander = queryExpander;
        this.curriculum = curriculum;
        this.depthInstructions = Map.of(
                "simple", simplePrompt.getContentAsString(StandardCharsets.UTF_8),
                "deep", deepPrompt.getContentAsString(StandardCharsets.UTF_8),
                "practice", practicePrompt.getContentAsString(StandardCharsets.UTF_8));
        this.lessonTemplate = lessonPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.answerCache = answerCache;
        this.releases = releases;
        this.costMeter = costMeter;
        this.usageGuard = usageGuard;
        this.chatMemory = chatMemory;
        this.retriever = retriever;
        this.collection = collection;
        this.sinhalaCollection = sinhalaCollection;
        this.sinhalaLayers = sinhalaLayers.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        this.answerSystemPrompt = answerPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.sinhalaInstructions = sinhalaPrompt.getContentAsString(StandardCharsets.UTF_8);
        var fingerprint = new ArrayList<>(List.of("answer-cache-v1", answerModel, expansionModel,
                String.valueOf(expansionEnabled), this.answerSystemPrompt, this.sinhalaInstructions, this.lessonTemplate));
        this.depthInstructions.keySet().stream().sorted().forEach(k -> fingerprint.add(this.depthInstructions.get(k)));
        this.answerFingerprint = AnswerCache.key(fingerprint);
    }

    /** For internal callers (evaluations): never rate-limited. */
    public AskResponse ask(AskRequest request) {
        return ask(request, null);
    }

    /**
     * @param person who is asking (see {@link AskController}), for the usage limits; null is not limited
     */
    public AskResponse ask(AskRequest request, String person) {
        long started = System.nanoTime();
        String conversationId = request.conversationId() == null
                ? UUID.randomUUID().toString() : request.conversationId();
        // English stays the default. Sinhala searches its suttas first, plus (unless "suttas only", or a depth
        // other than "deep") the Abhidhamma, commentaries and Visuddhimagga; the suttas stay the primary source.
        List<String> targetCollections = new ArrayList<>(List.of(request.sinhala() ? sinhalaCollection : collection));
        if (request.sinhala() && request.includeLayers()) {
            targetCollections.addAll(sinhalaLayers);
        }
        Lesson lesson = request.lessonId() == null ? null : curriculum.lesson(request.lessonId())
                .orElseThrow(() -> new CurriculumNotFoundException("lesson", request.lessonId()));
        Set<String> keyTextIds = lesson == null ? Set.of() : Set.copyOf(keyTextIds(lesson, request.sinhala()));

        // A first question asked exactly like an earlier one gets the stored answer: no model call at all.
        String cacheKey = request.conversationId() == null && answerCache.enabled()
                ? cacheKey(request, targetCollections, lesson) : null;
        if (cacheKey != null) {
            var cached = answerCache.get(cacheKey);
            if (cached.isPresent()) {
                AskResponse response = withConversation(cached.get(), conversationId);
                // Remember the exchange so a follow-up in this new conversation still has its context.
                chatMemory.add(conversationId, List.of(new UserMessage(request.question()),
                        new AssistantMessage(response.answer())));
                log.info("Answered from cache conversation={} depth={} lesson={} citations={} durationMs={}",
                        conversationId, request.depthOrDefault(), request.lessonId(), response.citations().size(),
                        (System.nanoTime() - started) / 1_000_000);
                return response;
            }
        }

        // One model call turns the question (rewritten first if it is a follow-up) into the searches to run.
        // Past the cache, this question costs money: count it against the limits first.
        usageGuard.acquire(person);
        QueryPlan plan = queryExpander.plan(request.question(), recentConversation(conversationId), request.sinhala());
        String searchQuery = plan.standaloneQuery();
        List<Document> passages = retriever.withContext(
                retriever.searchQueries(plan.queries(), targetCollections, keyTextIds));

        // Passages go in the system message so chat memory stores only the question, not the context.
        String system = answerSystemPrompt.replace("{{PASSAGES}}", formatPassages(passages))
                + instructions(request, lesson);

        var result = answerClient.prompt()
                .system(system)
                .user(request.question())
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                // Have the API enforce the schema; prompt-only JSON can come back malformed.
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .call()
                .responseEntity(AnswerDraft.class);

        String model = result.getResponse() == null ? null : result.getResponse().getMetadata().getModel();
        Usage usage = result.getResponse() == null ? null : result.getResponse().getMetadata().getUsage();
        logUsage(model, usage);
        double answerCost = costMeter.record("answer", model, usage);
        AskResponse response = AnswerAssembler.assemble(conversationId, searchQuery, result.entity(), passages);

        if (cacheKey != null && cacheable(passages, response)) {
            answerCache.put(cacheKey, response);
        }
        log.info("Question cost planUsd={} answerUsd={} totalUsd={} language={} depth={} lesson={}",
                usd(plan.costUsd()), usd(answerCost), usd(plan.costUsd() + answerCost),
                request.sinhala() ? "si" : "en", request.depthOrDefault(), request.lessonId());
        log.info("Answered conversation={} depth={} lesson={} passages={} citations={} durationMs={}",
                conversationId, request.depthOrDefault(), request.lessonId(), passages.size(), response.citations().size(),
                (System.nanoTime() - started) / 1_000_000);
        return response;
    }

    /**
     * Everything that shapes a first answer: the question, language, depth, lesson (as written now), the collections
     * searched with their current releases, and the prompt/model fingerprint.
     */
    String cacheKey(AskRequest request, List<String> collections, Lesson lesson) {
        var parts = new ArrayList<>(List.of(answerFingerprint, AnswerCache.normalize(request.question()),
                request.sinhala() ? "si" : "en", request.depthOrDefault(),
                lesson == null ? "-" : lesson.id() + "|" + lessonTitle(lesson, request.sinhala()) + "|"
                        + lessonKeyText(lesson, request.sinhala()) + "|" + keyTextIds(lesson, request.sinhala())));
        for (String c : collections) {
            parts.add(c + "@" + releases.current(c).orElse("none"));
        }
        return AnswerCache.key(parts);
    }

    /**
     * Only a full-quality answer is stored: passages were found and reranked (not the rate-limit fallback), and the
     * answer cites at least one of them. Anything less is answered again next time.
     */
    static boolean cacheable(List<Document> passages, AskResponse response) {
        boolean reranked = passages.stream()
                .map(d -> String.valueOf(d.getMetadata().get("rerankStatus")))
                .noneMatch(status -> status.equals("fallback") || status.equals("skipped"));
        return !passages.isEmpty() && reranked && !response.citations().isEmpty();
    }

    static AskResponse withConversation(AskResponse r, String conversationId) {
        return new AskResponse(conversationId, r.searchQuery(), r.answer(), r.keyConcepts(), r.citations(),
                r.mentalModel(), r.mermaid(), r.sourceDistribution(), r.unknowns(), r.followUps(), r.generatedBy());
    }

    /**
     * The lesson's key text in the collection being searched: tipitaka.lk keys for Sinhala, SuttaCentral ids for
     * English. A Sinhala lesson without Sinhala ids prefers nothing (the English ids would never match).
     */
    static List<String> keyTextIds(Lesson lesson, boolean sinhala) {
        if (!sinhala) {
            return lesson.textIds();
        }
        return lesson.si() == null ? List.of() : lesson.si().textIds();
    }

    static String lessonTitle(Lesson lesson, boolean sinhala) {
        return sinhala && lesson.si() != null && lesson.si().title() != null ? lesson.si().title() : lesson.title();
    }

    static String lessonKeyText(Lesson lesson, boolean sinhala) {
        return sinhala && lesson.si() != null && lesson.si().keyText() != null ? lesson.si().keyText() : lesson.keyText();
    }

    /** Depth, lesson and language instructions appended to the system prompt, in that order. */
    String instructions(AskRequest request, Lesson lesson) {
        var extra = new StringBuilder();
        String depth = depthInstructions.get(request.depthOrDefault());
        if (depth != null) {
            extra.append('\n').append(depth);
        }
        if (lesson != null) {
            extra.append('\n').append(lessonTemplate.replace("{{TITLE}}", lessonTitle(lesson, request.sinhala()))
                    .replace("{{KEY_TEXT}}", lessonKeyText(lesson, request.sinhala())));
        }
        if (request.sinhala()) {
            extra.append('\n').append(sinhalaInstructions);
        }
        return extra.toString();
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

    private static String usd(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static void logUsage(String model, Usage usage) {
        if (usage != null) {
            log.info("Model usage model={} promptTokens={} completionTokens={} totalTokens={}",
                    model, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }
    }
}
