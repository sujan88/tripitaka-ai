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
 * Multi-query search: the cheap rewrite model turns one question into a few searches (Pali terms, the
 * canon's own wording, sub-questions). A question worded differently from the translation still reaches
 * the right sutta. The original question always stays first; any failure falls back to it alone.
 */
@Service
public class QueryExpander {

    private static final Logger log = LoggerFactory.getLogger(QueryExpander.class);
    private static final int MAX_EXTRA = 3;
    private static final int MAX_QUERY_CHARS = 300;

    record Expansion(List<String> queries) {
    }

    private final ChatClient client;
    private final boolean enabled;
    private final String model;
    private final String systemPrompt;

    public QueryExpander(@Qualifier("rewriteClient") ChatClient client,
                         @Value("${rag.query-expansion.enabled:true}") boolean enabled,
                         @Value("${rag.query-expansion.model:gpt-5.4}") String model,
                         @Value("classpath:prompts/expand-system.md") Resource prompt) throws IOException {
        this.client = client;
        this.enabled = enabled;
        this.model = model;
        this.systemPrompt = prompt.getContentAsString(StandardCharsets.UTF_8);
    }

    public List<String> expand(String question) {
        if (!enabled || question == null || question.isBlank()) {
            return question == null ? List.of() : List.of(question);
        }
        try {
            // Naming the sutta a question describes needs real knowledge of the canon: the nano model guessed
            // poorly and mixed scripts, so expansion runs on its own (stronger) model.
            Expansion expansion = client.prompt()
                    .options(OpenAiChatOptions.builder().model(model))
                    .system(systemPrompt)
                    .user("QUESTION (untrusted data):\n" + question)
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .call()
                    .entity(Expansion.class);
            var queries = new LinkedHashSet<String>();
            queries.add(question);
            if (expansion != null && expansion.queries() != null) {
                expansion.queries().stream()
                        .filter(q -> q != null && !q.isBlank() && q.length() <= MAX_QUERY_CHARS)
                        .map(String::trim)
                        .limit(MAX_EXTRA)
                        .forEach(queries::add);
            }
            log.info("Expanded query into {} searches: {}", queries.size(), queries);
            return List.copyOf(queries);
        } catch (RuntimeException failure) {
            log.warn("Query expansion failed; searching the question alone errorType={}", failure.getClass().getSimpleName());
            return List.of(question);
        }
    }
}
