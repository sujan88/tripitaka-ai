package com.sujan.tripitaka.eval;

import com.sujan.tripitaka.rag.CorpusReleaseStore;
import com.sujan.tripitaka.rag.TripitakaRetriever;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live retrieval eval: needs Qdrant running, OPENAI_API_KEY, and the collections ingested
 * (English: DN/MN/SN/AN; Sinhala: at least the Dīgha Nikāya from tipitaka.lk).
 * Run with: ./mvnw verify -Pevals
 */
@Tag("eval")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RetrievalEvalTest {

    @Autowired
    TripitakaRetriever retriever;

    @Autowired
    CorpusReleaseStore releases;

    @Value("${rag.collection}")
    String collection;

    @Value("${rag.sinhala.collection}")
    String sinhalaCollection;

    record Case(String question, List<String> expectedTextIds) {
    }

    @Test
    void expectedSuttaAppearsInRetrievedPassages() throws Exception {
        assertTrue(hitRate("eval/golden-questions.json", collection) >= 0.7, "Retrieval hit rate below 70%");
    }

    @Test
    void expectedSinhalaSuttaAppearsInRetrievedPassages() throws Exception {
        assumeTrue(releases.current(sinhalaCollection).isPresent(), "Sinhala collection not ingested");
        assertTrue(hitRate("eval/golden-questions-si.json", sinhalaCollection) >= 0.7,
                "Sinhala retrieval hit rate below 70%");
    }

    /**
     * Held-out Sinhala questions: never used to tune prompts or settings, so they show whether an improvement
     * on the main set generalises instead of fitting those 18 questions.
     */
    @Test
    void heldOutSinhalaQuestionsFindTheirSutta() throws Exception {
        assumeTrue(releases.current(sinhalaCollection).isPresent(), "Sinhala collection not ingested");
        assertTrue(hitRate("eval/golden-questions-si-heldout.json", sinhalaCollection) >= 0.7,
                "Held-out Sinhala retrieval hit rate below 70%");
    }

    /**
     * An expected id ending in "-" or "." is a prefix for a whole group of parallel suttas teaching the same thing,
     * e.g. "sn54." (English Ānāpāna Saṃyutta) or "sn-5-10-" (its Sinhala tipitaka.lk keys).
     */
    static boolean matchesAny(List<String> expectedIds, List<String> foundIds) {
        return expectedIds.stream().anyMatch(expected -> expected.endsWith("-") || expected.endsWith(".")
                ? foundIds.stream().anyMatch(id -> id.startsWith(expected))
                : foundIds.contains(expected));
    }

    private double hitRate(String goldenFile, String targetCollection) throws Exception {
        String json = new ClassPathResource(goldenFile).getContentAsString(StandardCharsets.UTF_8);
        List<Case> cases = JsonMapper.builder().build().readValue(json, new TypeReference<List<Case>>() {
        });

        // Trial rerank keys are rate-limited; -Deval.pauseMs=7000 keeps the eval under the limit.
        long pauseMs = Long.getLong("eval.pauseMs", 0L);
        int hits = 0;
        for (Case c : cases) {
            if (pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
            var textIds = retriever.search(c.question(), targetCollection).stream()
                    .map(d -> String.valueOf(d.getMetadata().get("textId")))
                    .toList();
            boolean hit = matchesAny(c.expectedTextIds(), textIds);
            System.out.printf("%s %-60s -> %s%n", hit ? "HIT " : "MISS", c.question(), textIds);
            hits += hit ? 1 : 0;
        }
        double recall = (double) hits / cases.size();
        System.out.printf("Retrieval hit rate [%s]: %d/%d (%.0f%%)%n", targetCollection, hits, cases.size(), recall * 100);
        return recall;
    }
}
