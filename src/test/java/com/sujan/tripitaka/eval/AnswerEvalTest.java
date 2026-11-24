package com.sujan.tripitaka.eval;

import com.sujan.tripitaka.chat.AskService;
import com.sujan.tripitaka.model.AskRequest;
import com.sujan.tripitaka.model.AskResponse;
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

/**
 * Live end-to-end answer eval over the golden questions: does the final answer cite the sutta that
 * actually teaches the topic? Also reports citation count, answer length, diagram rate and latency,
 * so a model or context change can be compared before/after. It does not judge faithfulness.
 * Run with: ./mvnw verify -Pevals -Dtest=AnswerEvalTest (costs a few answer calls).
 */
@Tag("eval")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AnswerEvalTest {

    @Autowired
    AskService askService;

    @Value("${rag.answer-model}")
    String answerModel;

    @Value("${rag.context.neighbors}")
    int contextNeighbors;

    record Case(String question, List<String> expectedTextIds) {
    }

    @Test
    void answersCiteTheExpectedSutta() throws Exception {
        String json = new ClassPathResource("eval/golden-questions.json").getContentAsString(StandardCharsets.UTF_8);
        List<Case> cases = JsonMapper.builder().build().readValue(json, new TypeReference<List<Case>>() {
        });

        int cited = 0, citations = 0, answerChars = 0, diagrams = 0;
        long millis = 0;
        for (Case c : cases) {
            long started = System.currentTimeMillis();
            AskResponse response = askService.ask(new AskRequest(c.question(), null));
            millis += System.currentTimeMillis() - started;

            List<String> citedIds = response.citations().stream().map(AskResponse.Citation::textId).distinct().toList();
            boolean hit = RetrievalEvalTest.matchesAny(c.expectedTextIds(), citedIds);
            cited += hit ? 1 : 0;
            citations += response.citations().size();
            answerChars += response.answer().length();
            diagrams += response.mentalModel() == null ? 0 : 1;
            System.out.printf("EVAL %s %-60s cited=%s%n", hit ? "HIT " : "MISS", c.question(), citedIds);
        }

        int n = cases.size();
        System.out.printf("EVAL SUMMARY model=%s neighbors=%d expectedCited=%d/%d avgCitations=%.1f "
                        + "avgAnswerChars=%d diagrams=%d/%d avgSeconds=%.1f%n",
                answerModel, contextNeighbors, cited, n, (double) citations / n, answerChars / n,
                diagrams, n, millis / 1000.0 / n);
        assertTrue((double) cited / n >= 0.6, "Expected sutta cited in under 60% of answers");
    }
}
