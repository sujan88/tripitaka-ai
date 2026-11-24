package com.sujan.tripitaka.eval;

import com.sujan.tripitaka.eval.AnswerJudges.ChunkJudgement;
import com.sujan.tripitaka.eval.AnswerJudges.ClaimAssessment;
import com.sujan.tripitaka.eval.AnswerJudges.ClaimReview;
import com.sujan.tripitaka.eval.AnswerJudges.ClaimStatus;
import com.sujan.tripitaka.eval.AnswerJudges.RelevanceReview;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Java side of the judges: verdicts, quote checks and chunk accounting. No model calls. */
class AnswerJudgesTest {

    private static ClaimAssessment claim(String text, ClaimStatus status) {
        return new ClaimAssessment(text, status, "because");
    }

    @Test
    void supportedAndQualifiedClaimsPass() {
        var result = AnswerJudges.aggregate(new ClaimReview(List.of(
                claim("Craving is the origin of suffering.", ClaimStatus.SUPPORTED),
                claim("In other words, letting go ends it.", ClaimStatus.QUALIFIED_INFERENCE))));

        assertTrue(result.pass());
        assertEquals(2, result.claims());
    }

    @Test
    void oneUnsupportedClaimFailsTheAnswerEvenAmongSupportedOnes() {
        var result = AnswerJudges.aggregate(new ClaimReview(List.of(
                claim("Craving is the origin of suffering.", ClaimStatus.SUPPORTED),
                claim("The Buddha taught this at Vulture Peak.", ClaimStatus.UNSUPPORTED))));

        assertFalse(result.pass());
        assertEquals(1, result.unfaithful());
    }

    @Test
    void emptyOrIncompleteReviewNeverPasses() {
        assertFalse(AnswerJudges.aggregate(new ClaimReview(List.of())).pass());
        assertFalse(AnswerJudges.aggregate(null).pass());
        assertFalse(AnswerJudges.aggregate(new ClaimReview(List.of(new ClaimAssessment("x", null, "r")))).pass());
    }

    @Test
    void quotesMustComeFromTheAnswer() {
        String answer = "**Kamma** is intention.\nIt ripens  in this life or later.";

        assertDoesNotThrow(() -> AnswerJudges.validateQuotedClaims(new ClaimReview(List.of(
                claim("Kamma is intention.", ClaimStatus.SUPPORTED),
                claim("It ripens in this life or later.", ClaimStatus.SUPPORTED))), answer));
        assertThrows(IllegalStateException.class, () -> AnswerJudges.validateQuotedClaims(new ClaimReview(List.of(
                claim("Kamma is fate.", ClaimStatus.CONTRADICTED))), answer));
    }

    @Test
    void sinhalaQuotesMatchWithOrWithoutJoiners() {
        String answer = "කර්මය යනු චේතනාවයි.";
        String quoteWithJoiner = "කර්‍මය යනු චේතනාවයි.";

        assertDoesNotThrow(() -> AnswerJudges.validateQuotedClaims(new ClaimReview(List.of(
                claim(quoteWithJoiner, ClaimStatus.SUPPORTED))), answer));
    }

    @Test
    void precisionCountsRelevantCitedChunks() {
        var precision = AnswerJudges.precision(new RelevanceReview(List.of(
                new ChunkJudgement("a", true, "on topic"),
                new ChunkJudgement("b", false, "about almsfood"))), Set.of("a", "b"));

        assertEquals(2, precision.cited());
        assertEquals(1, precision.relevant());
        assertEquals(0.5, precision.share());
    }

    @Test
    void precisionReviewMustJudgeExactlyTheCitedChunks() {
        assertThrows(IllegalStateException.class, () -> AnswerJudges.precision(new RelevanceReview(List.of(
                new ChunkJudgement("a", true, "r"))), Set.of("a", "b")));
        assertThrows(IllegalStateException.class, () -> AnswerJudges.precision(new RelevanceReview(List.of(
                new ChunkJudgement("a", true, "r"), new ChunkJudgement("z", true, "r"))), Set.of("a")));
        assertThrows(IllegalStateException.class, () -> AnswerJudges.precision(new RelevanceReview(List.of(
                new ChunkJudgement("a", true, "r"), new ChunkJudgement("a", false, "r"))), Set.of("a")));
    }
}
