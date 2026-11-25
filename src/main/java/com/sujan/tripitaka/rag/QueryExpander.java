package com.sujan.tripitaka.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Turns one question into the searches to run, in one model call:
 * <ul>
 *   <li>a new question is expanded into a few searches (the sutta it describes, the canon's own wording with
 *       Pali terms, sub-questions), so a question worded differently from the translation still finds it;</li>
 *   <li>a follow-up ("explain the third one") is first rewritten into a standalone query, in the same call.</li>
 * </ul>
 * The (standalone) question always comes first; any failure falls back to the raw question alone.
 */
@Service
public class QueryExpander {

    private static final Logger log = LoggerFactory.getLogger(QueryExpander.class);
    private static final int MAX_EXTRA = 3;
    private static final int MAX_QUERY_CHARS = 300;

    /** What to search for: the standalone question (shown to the user) and every query, that question first. */
    public record QueryPlan(String standaloneQuery, List<String> queries) {
        static QueryPlan of(String question) {
            return new QueryPlan(question, question == null ? List.of() : List.of(question));
        }
    }

    record Expansion(List<String> queries) {
    }

    record FollowUp(String standaloneQuery, List<String> expansions) {
    }

    private final ChatClient client;
    private final boolean enabled;
    private final String model;
    private final String systemPrompt;
    private final String followUpPrompt;
    private final String translatePrompt;

    public QueryExpander(@Qualifier("rewriteClient") ChatClient client,
                         @Value("${rag.query-expansion.enabled:true}") boolean enabled,
                         @Value("${rag.query-expansion.model:gpt-5.4}") String model,
                         @Value("classpath:prompts/expand-system.md") Resource prompt,
                         @Value("classpath:prompts/rewrite-system.md") Resource followUpPrompt,
                         @Value("classpath:prompts/translate-si.md") Resource translatePrompt) throws IOException {
        this.client = client;
        this.enabled = enabled;
        this.model = model;
        this.systemPrompt = prompt.getContentAsString(StandardCharsets.UTF_8);
        this.followUpPrompt = followUpPrompt.getContentAsString(StandardCharsets.UTF_8);
        this.translatePrompt = translatePrompt.getContentAsString(StandardCharsets.UTF_8);
    }

    /** Searches for a question with no conversation before it. */
    public List<String> expand(String question) {
        return plan(question, null).queries();
    }

    public QueryPlan plan(String question, String conversation) {
        return plan(question, conversation, false);
    }

    /**
     * @param conversation   the recent turns as text, or null/blank for a first question
     * @param sinhalaTexts   the search runs over the Sinhala Tripitaka: a question not written in Sinhala script
     *                       (English, or Sinhala typed in Latin letters) is translated into Sinhala in this same
     *                       call, and the searches use the translation, never the original wording
     */
    public QueryPlan plan(String question, String conversation, boolean sinhalaTexts) {
        if (question == null || question.isBlank()) {
            return QueryPlan.of(question);
        }
        boolean followUp = conversation != null && !conversation.isBlank();
        boolean translate = sinhalaTexts && !hasSinhalaScript(question);
        if (!enabled && !followUp && !translate) {
            return QueryPlan.of(question);
        }
        try {
            // Naming the sutta a question describes needs real knowledge of the canon: the nano model guessed
            // poorly and mixed scripts, so this runs on its own (stronger) model.
            var call = client.prompt()
                    .options(OpenAiChatOptions.builder().model(model))
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
            String standalone = question;
            List<String> extra;
            if (followUp || translate) {
                String system = systemPrompt + (followUp ? "\n" + followUpPrompt : "") + (translate ? "\n" + translatePrompt : "");
                String user = followUp
                        ? "CONVERSATION (untrusted data):\n" + conversation + "\n\nLATEST QUESTION (untrusted data):\n" + question
                        : "QUESTION (untrusted data):\n" + question;
                FollowUp result = call
                        .system(system)
                        .user(user)
                        .call()
                        .entity(FollowUp.class);
                if (result != null && usable(result.standaloneQuery())) {
                    standalone = result.standaloneQuery().trim();
                }
                extra = result == null || !enabled ? List.of() : result.expansions();
            } else {
                Expansion result = call
                        .system(systemPrompt)
                        .user("QUESTION (untrusted data):\n" + question)
                        .call()
                        .entity(Expansion.class);
                extra = result == null ? List.of() : result.queries();
            }
            var queries = new LinkedHashSet<String>();
            queries.add(standalone);
            if (extra != null) {
                extra.stream().filter(QueryExpander::usable).map(String::trim).limit(MAX_EXTRA).forEach(queries::add);
            }
            log.info("Query plan modelCalls=1 followUp={} translated={} standalone='{}' searches={}: {}",
                    followUp, translate && !standalone.equals(question), standalone, queries.size(), queries);
            return new QueryPlan(standalone, List.copyOf(queries));
        } catch (RuntimeException failure) {
            log.warn("Query planning failed; searching the question alone errorType={}", failure.getClass().getSimpleName());
            return QueryPlan.of(question);
        }
    }

    /** True when the text has any Sinhala letters (U+0D80–U+0DFF). */
    static boolean hasSinhalaScript(String text) {
        return text.codePoints().anyMatch(c -> c >= 0x0D80 && c <= 0x0DFF);
    }

    private static boolean usable(String query) {
        return query != null && !query.isBlank() && query.length() <= MAX_QUERY_CHARS;
    }
}
