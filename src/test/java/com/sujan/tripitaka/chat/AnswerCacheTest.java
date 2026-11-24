package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.model.AskResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCacheTest {

    JdbcTemplate jdbc;

    @BeforeEach
    void database() throws Exception {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
        jdbc.execute(new ClassPathResource("db/migration/V2__create_answer_cache.sql").getContentAsString(StandardCharsets.UTF_8));
    }

    private static AskResponse response(String conversationId) {
        return new AskResponse(conversationId, "q", "The answer", List.of("kamma"),
                List.of(new AskResponse.Citation("c1", "mn135", "Title", "Majjhima Nikāya", "mn135:1.1", "excerpt", "Sutta")),
                null, null, Map.of("Majjhima Nikāya", 1), List.of(), List.of("Next?"), "ai");
    }

    private AnswerCache cache(Instant now) {
        return new AnswerCache(jdbc, true, Duration.ofDays(30), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void storesAndReturnsTheWholeAnswer() {
        var cache = cache(Instant.parse("2026-11-24T10:00:00Z"));
        cache.put("k", response("conv-1"));

        var hit = cache.get("k").orElseThrow();

        assertEquals("The answer", hit.answer());
        assertEquals("Sutta", hit.citations().get(0).layer());
        assertEquals(List.of("Next?"), hit.followUps());
        assertEquals("ai", hit.generatedBy());
        assertEquals(1, jdbc.queryForObject("SELECT hits FROM answer_cache WHERE cache_key = 'k'", Integer.class));
    }

    @Test
    void expiredAnswersAreMisses() {
        cache(Instant.parse("2026-01-01T00:00:00Z")).put("k", response("c"));

        assertTrue(cache(Instant.parse("2026-01-20T00:00:00Z")).get("k").isPresent());
        assertFalse(cache(Instant.parse("2026-02-05T00:00:00Z")).get("k").isPresent());
    }

    @Test
    void disabledCacheNeverStoresOrReturns() {
        var off = new AnswerCache(jdbc, false, Duration.ofDays(30), Clock.systemUTC());
        off.put("k", response("c"));

        assertFalse(off.get("k").isPresent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM answer_cache", Integer.class));
    }

    @Test
    void keyDependsOnEveryPartAndQuestionsAreNormalised() {
        assertEquals(AnswerCache.normalize("  What is  Kamma? "), AnswerCache.normalize("what is kamma?"));
        assertNotEquals(AnswerCache.key(List.of("ab", "c")), AnswerCache.key(List.of("a", "bc")));
        assertNotEquals(AnswerCache.key(List.of("q", "sutta-en@r1")), AnswerCache.key(List.of("q", "sutta-en@r2")));
    }

    private static Document passage(String rerankStatus) {
        return Document.builder().id(UUID.randomUUID().toString()).text("t")
                .metadata(rerankStatus == null ? Map.of() : Map.of("rerankStatus", rerankStatus)).build();
    }

    @Test
    void onlyFullQualityAnswersAreCached() {
        var cited = response("c");
        var uncited = new AskResponse("c", "q", "a", List.of(), List.of(), null, null, Map.of(), List.of(), List.of(), "ai");

        assertTrue(AskService.cacheable(List.of(passage("success"), passage(null)), cited));
        assertTrue(AskService.cacheable(List.of(passage("disabled")), cited)); // no reranker configured
        assertFalse(AskService.cacheable(List.of(passage("fallback")), cited)); // rate-limited rerank
        assertFalse(AskService.cacheable(List.of(), cited));
        assertFalse(AskService.cacheable(List.of(passage("success")), uncited));
    }

    @Test
    void aCachedAnswerIsServedUnderTheNewConversation() {
        assertEquals("new", AskService.withConversation(response("old"), "new").conversationId());
    }
}
