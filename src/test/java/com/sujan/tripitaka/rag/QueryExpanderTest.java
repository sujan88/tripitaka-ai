package com.sujan.tripitaka.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryExpanderTest {

    /** Answers every call with fixed JSON and records the prompts it was sent. */
    static class StubModel implements ChatModel {
        final List<Prompt> prompts = new ArrayList<>();
        final Supplier<String> reply;

        StubModel(Supplier<String> reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply.get()))));
        }
    }

    private static QueryExpander expander(StubModel model) throws Exception {
        return new QueryExpander(ChatClient.builder(model).build(), true, "gpt-5.4",
                new ByteArrayResource("EXPAND".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayResource("FOLLOW-UP".getBytes(StandardCharsets.UTF_8)),
                new ByteArrayResource("TRANSLATE".getBytes(StandardCharsets.UTF_8)), null);
    }

    @Test
    void firstQuestionIsExpandedInOneCall() throws Exception {
        var model = new StubModel(() -> "{\"queries\":[\"Kakacūpama Sutta\",\"simile of the saw\"]}");

        var plan = expander(model).plan("the simile of the saw", null);

        assertEquals(1, model.prompts.size());
        assertEquals("the simile of the saw", plan.standaloneQuery());
        assertEquals(List.of("the simile of the saw", "Kakacūpama Sutta", "simile of the saw"), plan.queries());
    }

    @Test
    void followUpIsRewrittenAndExpandedInOneCall() throws Exception {
        var model = new StubModel(() ->
                "{\"standaloneQuery\":\"the third noble truth\",\"expansions\":[\"nirodha sacca\",\"cessation of dukkha\"]}");

        var plan = expander(model).plan("explain the third one", "USER: what are the four noble truths?");

        assertEquals(1, model.prompts.size());
        assertTrue(model.prompts.get(0).getContents().contains("FOLLOW-UP"));
        assertTrue(model.prompts.get(0).getContents().contains("four noble truths"));
        assertEquals("the third noble truth", plan.standaloneQuery());
        assertEquals(List.of("the third noble truth", "nirodha sacca", "cessation of dukkha"), plan.queries());
    }

    @Test
    void anyFailureFallsBackToTheRawQuestion() throws Exception {
        var model = new StubModel(() -> {
            throw new IllegalStateException("model down");
        });

        var plan = expander(model).plan("explain the third one", "USER: what are the four noble truths?");

        assertEquals("explain the third one", plan.standaloneQuery());
        assertEquals(List.of("explain the third one"), plan.queries());
    }

    @Test
    void blankStandaloneQueryKeepsTheQuestion() throws Exception {
        var model = new StubModel(() -> "{\"standaloneQuery\":\" \",\"expansions\":[\"x\"]}");

        var plan = expander(model).plan("and the second?", "USER: earlier");

        assertEquals("and the second?", plan.standaloneQuery());
        assertEquals(List.of("and the second?", "x"), plan.queries());
    }

    @Test
    void englishQuestionOnSinhalaTextsIsTranslatedInTheSameCallAndSearchedOnlyInSinhala() throws Exception {
        var model = new StubModel(() ->
                "{\"standaloneQuery\":\"කර්මය යනු කුමක්ද?\",\"expansions\":[\"චේතනාව කර්මය\"]}");

        var plan = expander(model).plan("What is kamma?", null, true);

        assertEquals(1, model.prompts.size());
        assertTrue(model.prompts.get(0).getContents().contains("TRANSLATE"));
        assertEquals("කර්මය යනු කුමක්ද?", plan.standaloneQuery());
        assertEquals(List.of("කර්මය යනු කුමක්ද?", "චේතනාව කර්මය"), plan.queries()); // no English search
    }

    @Test
    void sinhalaQuestionsAndEnglishTextsAreNotTranslated() throws Exception {
        var model = new StubModel(() -> "{\"queries\":[\"x\"]}");

        expander(model).plan("කර්මය යනු කුමක්ද?", null, true);
        expander(model).plan("What is kamma?", null, false);

        assertEquals(2, model.prompts.size());
        model.prompts.forEach(p -> assertTrue(!p.getContents().contains("TRANSLATE")));
    }

    @Test
    void sinhalaTypedInLatinLettersCountsAsNotSinhalaScript() {
        assertTrue(!QueryExpander.hasSinhalaScript("karmaya kiyanne mokakda"));
        assertTrue(QueryExpander.hasSinhalaScript("කර්මය"));
    }

    @Test
    void failedTranslationFallsBackToTheQuestion() throws Exception {
        var model = new StubModel(() -> {
            throw new IllegalStateException("down");
        });

        assertEquals(List.of("What is kamma?"), expander(model).plan("What is kamma?", null, true).queries());
    }
}
