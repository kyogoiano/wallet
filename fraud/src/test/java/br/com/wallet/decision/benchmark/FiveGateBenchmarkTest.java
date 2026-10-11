package br.com.wallet.decision.benchmark;

import br.com.wallet.decision.catalog.FraudDecisionQuestions;
import br.com.wallet.decision.evaluator.DecisionEvaluator;
import br.com.wallet.decision.model.BooleanDecision;
import br.com.wallet.decision.model.Confidence;
import br.com.wallet.decision.model.DecisionAnswer;
import br.com.wallet.decision.model.DecisionEvidence;
import br.com.wallet.decision.model.DecisionProvenance;
import br.com.wallet.decision.model.EvaluatedQuestion;
import br.com.wallet.decision.model.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FiveGateBenchmarkHarness Semantic Evaluation Protocol Tests (REQ-TYPED-002)")
class FiveGateBenchmarkTest {

    private final DecisionProvenance prov = new DecisionProvenance("candidate-model", "v1.0", Instant.now(), 5L);

    @Test
    @DisplayName("POSITIVE: Candidate satisfying all 5 gates receives full certification")
    void shouldCertifyValidCandidate() {
        DecisionEvidence ev1 = DecisionEvidence.of("acc_1", "Account profile", Map.of("daysActive", 300));
        BenchmarkSample sample1 = new BenchmarkSample(
            "ACC-1",
            FraudDecisionQuestions.BEHAVIOR_ANOMALY,
            Map.of("acc_1", ev1),
            new BooleanDecision(true, "Anomalous surge")
        );

        DecisionEvaluator candidateEvaluator = (subjectId, questions, evidence) -> {
            EvaluatedQuestion<BooleanDecision> eq = new EvaluatedQuestion<>(
                FraudDecisionQuestions.BEHAVIOR_ANOMALY,
                new DecisionAnswer<>(
                    new BooleanDecision(true, "Anomalous surge"),
                    Confidence.HIGH,
                    List.of(ev1),
                    prov
                )
            );
            return CompletableFuture.completedFuture(
                new EvaluationResult("EVAL-1", subjectId, List.of(eq), Instant.now())
            );
        };

        FiveGateBenchmarkHarness harness = new FiveGateBenchmarkHarness(
            new BigDecimal("0.85"), // Baseline F1
            new BigDecimal("0.02"), // Epsilon workload margin
            new BigDecimal("0.10"), // Baseline ECE
            new BigDecimal("0.05")  // Epsilon calibration margin
        );

        BenchmarkCertification cert = harness.evaluateCandidate(candidateEvaluator, List.of(sample1));

        assertThat(cert.certified()).isTrue();
        assertThat(cert.gateReports()).hasSize(5);
        assertThat(cert.gateReports()).allMatch(GateReport::passed);
    }

    @Test
    @DisplayName("GATE 2 FAILURE: Hallucinated evidence hash causes immediate Gate 2 failure")
    void shouldFailGate2WhenEvidenceHashIsHallucinated() {
        DecisionEvidence realEvidence = DecisionEvidence.of("acc_1", "Summary", Map.of("risk", "low"));
        DecisionEvidence fakeEvidence = new DecisionEvidence("fake", "Summary", Map.of(), "tampered_fake_hash_123");

        BenchmarkSample sample = new BenchmarkSample(
            "ACC-1",
            FraudDecisionQuestions.BEHAVIOR_ANOMALY,
            Map.of("acc_1", realEvidence),
            new BooleanDecision(false, "Normal")
        );

        DecisionEvaluator ungroundedEvaluator = (subjectId, questions, evidence) -> {
            EvaluatedQuestion<BooleanDecision> eq = new EvaluatedQuestion<>(
                FraudDecisionQuestions.BEHAVIOR_ANOMALY,
                new DecisionAnswer<>(
                    new BooleanDecision(false, "Normal"),
                    Confidence.HIGH,
                    List.of(fakeEvidence), // Not present in input evidence!
                    prov
                )
            );
            return CompletableFuture.completedFuture(
                new EvaluationResult("EVAL-2", subjectId, List.of(eq), Instant.now())
            );
        };

        FiveGateBenchmarkHarness harness = new FiveGateBenchmarkHarness(
            new BigDecimal("0.80"), new BigDecimal("0.02"),
            new BigDecimal("0.10"), new BigDecimal("0.05")
        );

        BenchmarkCertification cert = harness.evaluateCandidate(ungroundedEvaluator, List.of(sample));

        assertThat(cert.certified()).isFalse();
        GateReport gate2 = cert.gateReports().stream()
            .filter(r -> r.gateNumber() == 2)
            .findFirst()
            .orElseThrow();
        assertThat(gate2.passed()).isFalse();
        assertThat(gate2.diagnostic()).contains("Grounding verification failed");
    }

    @Test
    @DisplayName("GATE 3 FAILURE: Directed correctness regression beyond epsilon causes rejection")
    void shouldFailGate3WhenRegressionExceedsEpsilon() {
        DecisionEvidence ev = DecisionEvidence.of("acc_1", "Summary", Map.of("days", 10));
        // Target is true, but evaluator predicts false -> F1 = 0.0
        BenchmarkSample sample = new BenchmarkSample(
            "ACC-1",
            FraudDecisionQuestions.BEHAVIOR_ANOMALY,
            Map.of("acc_1", ev),
            new BooleanDecision(true, "Expected true anomaly")
        );

        DecisionEvaluator inaccurateEvaluator = (subjectId, questions, evidence) -> {
            EvaluatedQuestion<BooleanDecision> eq = new EvaluatedQuestion<>(
                FraudDecisionQuestions.BEHAVIOR_ANOMALY,
                new DecisionAnswer<>(
                    new BooleanDecision(false, "Inaccurate prediction"),
                    Confidence.HIGH,
                    List.of(ev),
                    prov
                )
            );
            return CompletableFuture.completedFuture(
                new EvaluationResult("EVAL-3", subjectId, List.of(eq), Instant.now())
            );
        };

        FiveGateBenchmarkHarness harness = new FiveGateBenchmarkHarness(
            new BigDecimal("0.90"), // High baseline
            new BigDecimal("0.02"), // Epsilon margin
            new BigDecimal("0.10"),
            new BigDecimal("0.05")
        );

        BenchmarkCertification cert = harness.evaluateCandidate(inaccurateEvaluator, List.of(sample));

        assertThat(cert.certified()).isFalse();
        GateReport gate3 = cert.gateReports().stream()
            .filter(r -> r.gateNumber() == 3)
            .findFirst()
            .orElseThrow();
        assertThat(gate3.passed()).isFalse();
        assertThat(gate3.diagnostic()).contains("Task correctness non-inferiority margin violated");
    }
}
