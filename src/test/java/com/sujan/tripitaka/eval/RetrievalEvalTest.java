package com.sujan.tripitaka.eval;

import com.sujan.tripitaka.rag.CorpusReleaseStore;
import com.sujan.tripitaka.rag.TripitakaRetriever;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live retrieval eval: needs Qdrant running, OPENAI_API_KEY, and the collections ingested
 * (English: DN/MN/SN/AN; Sinhala: at least the Dīgha Nikāya from tipitaka.lk).
 * Scores go to {@link EvalReport}: each fails when more than one case below eval/baseline.json.
 * Run with: ./mvnw verify -Pevals
 */
@Tag("eval")
@SpringBootTest(properties = "rag.answer-cache.enabled=false") // evals measure fresh answers
@ExtendWith(EvalReport.class)
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
        hitRate("retrieval-en", "Retrieval English", "eval/golden-questions.json", List.of(collection));
    }

    @Test
    void expectedSinhalaSuttaAppearsInRetrievedPassages() throws Exception {
        assumeTrue(releases.current(sinhalaCollection).isPresent(), "Sinhala collection not ingested");
        hitRate("retrieval-si", "Retrieval Sinhala (suttas only)", "eval/golden-questions-si.json",
                List.of(sinhalaCollection));
    }

    /**
     * Held-out Sinhala questions: never used to tune prompts or settings, so they show whether an improvement
     * on the main set generalises instead of fitting those 18 questions.
     */
    @Test
    void heldOutSinhalaQuestionsFindTheirSutta() throws Exception {
        assumeTrue(releases.current(sinhalaCollection).isPresent(), "Sinhala collection not ingested");
        hitRate("retrieval-si-heldout", "Retrieval Sinhala held-out", "eval/golden-questions-si-heldout.json",
                List.of(sinhalaCollection));
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
        hitRate("retrieval-si-layers", "Retrieval Sinhala (all layers)", "eval/golden-questions-si.json", allSinhala());
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
        long millis = 0;
        for (Case c : cases) {
            if (pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
            long started = System.currentTimeMillis();
            var found = retriever.search(c.question(), allSinhala());
            millis += System.currentTimeMillis() - started;
            var layers = found.stream().map(d -> String.valueOf(d.getMetadata().get("collection"))).toList();
            boolean ok = layers.contains(sinhalaCollection) && layers.stream().anyMatch(l -> !l.equals(sinhalaCollection));
            good += ok ? 1 : 0;
            System.out.printf("%s %-50s -> %s%n", ok ? "HIT " : "MISS", c.question(),
                    found.stream().map(d -> d.getMetadata().get("collection") + ":" + d.getMetadata().get("textId")).toList());
        }
        System.out.printf("Deep questions drawing on suttas + explanatory layers: %d/%d%n", good, cases.size());
        EvalReport.record("retrieval-si-deep", "Deep questions use suttas + layers", good, cases.size(),
                Map.of("avgSearchSeconds", seconds(millis, cases.size())));
    }

    private List<String> allSinhala() {
        var all = new java.util.ArrayList<String>();
        all.add(sinhalaCollection);
        all.addAll(sinhalaLayers);
        return all;
    }

    private void hitRate(String key, String label, String goldenFile, List<String> targetCollections) throws Exception {
        String json = new ClassPathResource(goldenFile).getContentAsString(StandardCharsets.UTF_8);
        List<Case> cases = JsonMapper.builder().build().readValue(json, new TypeReference<List<Case>>() {
        });

        // Trial rerank keys are rate-limited; -Deval.pauseMs=7000 keeps the eval under the limit.
        long pauseMs = Long.getLong("eval.pauseMs", 0L);
        int hits = 0;
        long millis = 0;
        var misses = new java.util.ArrayList<String>();
        for (Case c : cases) {
            if (pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
            long started = System.currentTimeMillis();
            var textIds = retriever.search(c.question(), targetCollections).stream()
                    .map(d -> String.valueOf(d.getMetadata().get("textId")))
                    .toList();
            millis += System.currentTimeMillis() - started;
            boolean hit = matchesAny(c.expectedTextIds(), textIds);
            System.out.printf("%s %-60s -> %s%n", hit ? "HIT " : "MISS", c.question(), textIds);
            hits += hit ? 1 : 0;
            if (!hit) {
                misses.add(c.question());
            }
        }
        System.out.printf("Retrieval hit rate %s: %d/%d%n", targetCollections, hits, cases.size());
        EvalReport.record(key, label, hits, cases.size(),
                Map.of("avgSearchSeconds", seconds(millis, cases.size()), "misses", misses));
    }

    private static double seconds(long millis, int n) {
        return Math.round(millis / 10.0 / n) / 100.0;
    }
}
