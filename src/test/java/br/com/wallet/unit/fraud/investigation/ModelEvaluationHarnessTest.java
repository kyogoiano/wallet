package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.ClaimType;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationClaim;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.internal.benchmark.ModelEvaluationHarness;
import br.com.wallet.fraud.investigation.internal.grounding.ClaimGroundingValidator;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import br.com.wallet.fraud.investigation.spi.InferenceBenchmarkThresholds;
import br.com.wallet.fraud.investigation.spi.LocalInferenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModelEvaluationHarness Unit Tests (Benchmark Gate Assertion REQ-VEC-007)")
class ModelEvaluationHarnessTest {

    @Mock
    private LocalInferenceClient inferenceClient;

    private ClaimGroundingValidator groundingValidator;
    private ModelEvaluationHarness harness;

    @BeforeEach
    void setUp() {
        groundingValidator = new ClaimGroundingValidator();
        harness = new ModelEvaluationHarness(inferenceClient, groundingValidator);
    }

    @Test
    @DisplayName("REQ-VEC-007: Should pass benchmark gate when candidate meets 100% validity and latency budgets")
    void shouldPassBenchmarkGate() {
        InvestigationEvidence evidence = new InvestigationEvidence(
            FraudRiskSnapshot.empty(),
            List.of(new AtomicEvidenceItem("GRAPH-001", "CLUSTER", "USER", Map.of("count", 3)))
        );

        SanitizedInferenceContext context = new SanitizedInferenceContext(
            "MASK_USER_TARGET", evidence.risks(), evidence.evidenceItems(),
            RiskClassification.HIGH, List.of(RecommendedAction.MANUAL_REVIEW)
        );

        ModelEvaluationHarness.BenchmarkCase bCase = new ModelEvaluationHarness.BenchmarkCase(
            "CASE-001-money-mule", context, evidence
        );

        InvestigationNarrative validNarrative = new InvestigationNarrative(
            "Mule summary.",
            List.of(new InvestigationClaim(ClaimType.SHARED_INFRASTRUCTURE, "Cluster claim.", List.of("GRAPH-001"))),
            "Action rationale."
        );

        when(inferenceClient.generateNarrative(any())).thenReturn(Optional.of(validNarrative));

        InferenceBenchmarkThresholds thresholds = new InferenceBenchmarkThresholds(
            Duration.ofSeconds(1), Duration.ofSeconds(2), 1.0, 1.0
        );

        ModelEvaluationHarness.BenchmarkReport report = harness.evaluate(List.of(bCase), thresholds);

        assertThat(report.passedGate()).isTrue();
        assertThat(report.jsonValidityRate()).isEqualTo(1.0);
        assertThat(report.groundingValidityRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("REQ-VEC-007: Should fail benchmark gate when model introduces hallucinated claims")
    void shouldFailGateOnHallucination() {
        InvestigationEvidence evidence = new InvestigationEvidence(
            FraudRiskSnapshot.empty(),
            List.of(new AtomicEvidenceItem("GRAPH-001", "CLUSTER", "USER", Map.of("count", 3)))
        );

        SanitizedInferenceContext context = new SanitizedInferenceContext(
            "MASK_USER_TARGET", evidence.risks(), evidence.evidenceItems(),
            RiskClassification.HIGH, List.of(RecommendedAction.MANUAL_REVIEW)
        );

        ModelEvaluationHarness.BenchmarkCase bCase = new ModelEvaluationHarness.BenchmarkCase(
            "CASE-001-money-mule", context, evidence
        );

        // References hallucinated ID "HALLUCINATED-999"
        InvestigationNarrative hallucinatedNarrative = new InvestigationNarrative(
            "Mule summary.",
            List.of(new InvestigationClaim(ClaimType.SHARED_INFRASTRUCTURE, "Cluster claim.", List.of("HALLUCINATED-999"))),
            "Action rationale."
        );

        when(inferenceClient.generateNarrative(any())).thenReturn(Optional.of(hallucinatedNarrative));

        InferenceBenchmarkThresholds thresholds = new InferenceBenchmarkThresholds(
            Duration.ofSeconds(1), Duration.ofSeconds(2), 1.0, 1.0
        );

        ModelEvaluationHarness.BenchmarkReport report = harness.evaluate(List.of(bCase), thresholds);

        assertThat(report.passedGate()).isFalse();
        assertThat(report.groundingValidityRate()).isEqualTo(0.0);
    }
}
