package com.sujan.tripitaka.eval;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One report for every eval in the run. Each eval records its score as cases passed out of cases run; the score is
 * compared with {@code eval/baseline.json} and the eval fails if it is more than {@code eval.tolerance} cases (default
 * 1) below its baseline. When the whole test run ends, all scores are printed in one table and written to
 * {@code target/eval-report.json}. Any drop, even within tolerance, is marked DOWN so it stays visible.
 */
public class EvalReport implements BeforeAllCallback {

    /** One eval's score. {@code detail} holds extra numbers such as claim-level shares or latency. */
    public record Score(String key, String label, int passed, int total, Map<String, Object> detail) {
    }

    private static final Map<String, Score> SCORES = new LinkedHashMap<>();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path REPORT = Path.of("target", "eval-report.json");

    @Override
    public void beforeAll(ExtensionContext context) {
        // A resource in the root store is closed once, after every test class of the run has finished.
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .computeIfAbsent(EvalReport.class.getName(), key -> (AutoCloseable) EvalReport::printTable, Object.class);
    }

    /** Records a score, writes the report, and fails the calling eval if it regressed past the tolerance. */
    public static synchronized void record(String key, String label, int passed, int total, Map<String, Object> detail) {
        SCORES.put(key, new Score(key, label, passed, total, detail));
        write();
        Integer baseline = baseline(key);
        if (baseline != null && passed < baseline - tolerance()) {
            fail("EVAL REGRESSION %s: %d/%d is more than %d case(s) below the baseline %d"
                    .formatted(label, passed, total, tolerance(), baseline));
        }
    }

    static int tolerance() {
        return Integer.getInteger("eval.tolerance", 1);
    }

    static Integer baseline(String key) {
        JsonNode node = baselines().get(key);
        return node == null || node.isNull() ? null : node.get("passed").asInt();
    }

    static String status(Score score) {
        Integer baseline = baseline(score.key());
        if (baseline == null) {
            return "NEW";
        }
        if (score.passed() < baseline - tolerance()) {
            return "FAIL";
        }
        if (score.passed() < baseline) {
            return "DOWN";
        }
        return score.passed() > baseline ? "UP" : "OK";
    }

    private static JsonNode baselines() {
        try (InputStream in = EvalReport.class.getResourceAsStream("/eval/baseline.json")) {
            return in == null ? JSON.createObjectNode() : JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("Unreadable eval/baseline.json", failure);
        }
    }

    private static void write() {
        var report = new LinkedHashMap<String, Object>();
        report.put("tolerance", tolerance());
        var scores = new LinkedHashMap<String, Object>();
        for (Score score : SCORES.values()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("label", score.label());
            entry.put("passed", score.passed());
            entry.put("total", score.total());
            entry.put("baseline", baseline(score.key()));
            entry.put("status", status(score));
            entry.putAll(score.detail());
            scores.put(score.key(), entry);
        }
        report.put("scores", scores);
        try {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not write " + REPORT, failure);
        }
    }

    static synchronized void printTable() {
        if (SCORES.isEmpty()) {
            return;
        }
        var out = new StringBuilder("\n===== EVAL REPORT (tolerance %d case) =====\n".formatted(tolerance()));
        out.append("%-44s %9s %9s %-6s %s%n".formatted("Eval", "Score", "Baseline", "Status", "Detail"));
        for (Score score : SCORES.values()) {
            Integer baseline = baseline(score.key());
            out.append("%-44s %9s %9s %-6s %s%n".formatted(score.label(), score.passed() + "/" + score.total(),
                    baseline == null ? "-" : baseline + "/" + score.total(), status(score), score.detail()));
        }
        out.append("Report: ").append(REPORT.toAbsolutePath()).append('\n');
        System.out.println(out);
    }
}
