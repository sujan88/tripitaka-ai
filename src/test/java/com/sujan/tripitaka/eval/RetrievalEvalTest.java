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

    @Value("${rag.sinhala.layers}")
    List<String> sinhalaLayers;

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

    /** Searching suttas + Abhidhamma + commentaries + Visuddhimagga must not lose the suttas the questions ask for. */
    @Test
    void sinhalaSuttaQuestionsStillFindTheirSuttaWithAllLayers() throws Exception {
        assumeTrue(releases.current(sinhalaCollection).isPresent(), "Sinhala collection not ingested");
        assertTrue(hitRate("eval/golden-questions-si.json", allSinhala()) >= 0.7,
                "Sinhala retrieval with all layers below 70%");
    }

    /**
     * Deep questions (karma, rebirth-linking, the process of mind) should draw on the explanatory layers while
     * still including suttas. Held out: never used to tune anything.
     */
    @Test
    void deepQuestionsDrawOnExplanatoryLayersAndSuttas() throws Exception {
        assumeTrue(sinhalaLayers.stream().anyMatch(c -> releases.current(c).isPresent()), "No layer ingested");
        String json = new ClassPathResource("eval/golden-questions-si-deep.json").getContentAsString(StandardCharsets.UTF_8);
        List<Case> cases = JsonMapper.builder().build().readValue(json, new TypeReference<List<Case>>() {
        });
        long pauseMs = Long.getLong("eval.pauseMs", 0L);
        int good = 0;
        for (Case c : cases) {
            if (pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
            var found = retriever.search(c.question(), allSinhala());
            var layers = found.stream().map(d -> String.valueOf(d.getMetadata().get("collection"))).toList();
            boolean ok = layers.contains(sinhalaCollection) && layers.stream().anyMatch(l -> !l.equals(sinhalaCollection));
            good += ok ? 1 : 0;
            System.out.printf("%s %-50s -> %s%n", ok ? "HIT " : "MISS", c.question(),
                    found.stream().map(d -> d.getMetadata().get("collection") + ":" + d.getMetadata().get("textId")).toList());
        }
        double rate = (double) good / cases.size();
        System.out.printf("Deep questions drawing on suttas + explanatory layers: %d/%d (%.0f%%)%n", good, cases.size(), rate * 100);
        assertTrue(rate >= 0.7, "Deep questions drew on both suttas and explanatory layers in under 70%");
    }

    private List<String> allSinhala() {
        var all = new java.util.ArrayList<String>();
        all.add(sinhalaCollection);
        all.addAll(sinhalaLayers);
        return all;
    }

    private double hitRate(String goldenFile, String targetCollection) throws Exception {
        return hitRate(goldenFile, List.of(targetCollection));
    }

    private double hitRate(String goldenFile, List<String> targetCollections) throws Exception {
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
            var textIds = retriever.search(c.question(), targetCollections).stream()
                    .map(d -> String.valueOf(d.getMetadata().get("textId")))
                    .toList();
            boolean hit = matchesAny(c.expectedTextIds(), textIds);
            System.out.printf("%s %-60s -> %s%n", hit ? "HIT " : "MISS", c.question(), textIds);
            hits += hit ? 1 : 0;
        }
        double recall = (double) hits / cases.size();
        System.out.printf("Retrieval hit rate %s: %d/%d (%.0f%%)%n", targetCollections, hits, cases.size(), recall * 100);
        return recall;
    }
}
