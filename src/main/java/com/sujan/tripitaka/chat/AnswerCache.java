package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.model.AskResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Stores answers to first questions so the same question, asked the same way, is answered again without any model
 * call: lessons and Dhamma questions are asked word for word by every reader. The key (see {@link #key}) covers
 * everything that shapes an answer, so a new corpus release, model or prompt simply misses the cache. Entries
 * expire after {@code rag.answer-cache.ttl-days}. Any storage failure is logged and treated as a miss.
 */
@Repository
public class AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public AnswerCache(JdbcTemplate jdbc,
                       @Value("${rag.answer-cache.enabled:true}") boolean enabled,
                       @Value("${rag.answer-cache.ttl-days:30}") int ttlDays) {
        this(jdbc, enabled, Duration.ofDays(ttlDays), Clock.systemUTC());
    }

    AnswerCache(JdbcTemplate jdbc, boolean enabled, Duration ttl, Clock clock) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.ttl = ttl;
        this.clock = clock;
    }

    public boolean enabled() {
        return enabled;
    }

    /** SHA-256 of the parts, separated so ("ab", "c") and ("a", "bc") differ. */
    public static String key(List<String> parts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Same question in different spacing or case is the same question. */
    public static String normalize(String question) {
        return question == null ? "" : question.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    public Optional<AskResponse> get(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            var rows = jdbc.queryForList("SELECT response, created_at FROM answer_cache WHERE cache_key = ?", key);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            var created = rows.get(0).get("CREATED_AT");
            OffsetDateTime createdAt = created instanceof OffsetDateTime odt ? odt
                    : OffsetDateTime.parse(String.valueOf(created).replace(' ', 'T'));
            if (createdAt.plus(ttl).isBefore(now())) {
                jdbc.update("DELETE FROM answer_cache WHERE cache_key = ?", key);
                return Optional.empty();
            }
            jdbc.update("UPDATE answer_cache SET hits = hits + 1 WHERE cache_key = ?", key);
            return Optional.of(JSON.readValue(String.valueOf(rows.get(0).get("RESPONSE")), AskResponse.class));
        } catch (RuntimeException failure) {
            log.warn("Answer cache read failed; answering normally errorType={}", failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(String key, AskResponse response) {
        if (!enabled) {
            return;
        }
        try {
            String json = JSON.writeValueAsString(response);
            jdbc.update("DELETE FROM answer_cache WHERE cache_key = ?", key);
            jdbc.update("INSERT INTO answer_cache (cache_key, response, created_at) VALUES (?, ?, ?)", key, json, now());
        } catch (RuntimeException failure) {
            log.warn("Answer cache write failed errorType={}", failure.getClass().getSimpleName());
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
