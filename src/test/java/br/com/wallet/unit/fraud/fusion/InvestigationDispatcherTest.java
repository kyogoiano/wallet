package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.api.CheckpointRepository;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.RiskAttribution;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.internal.orchestration.InvestigationDispatcher;
import br.com.wallet.fraud.fusion.internal.policy.RiskDecisionPolicy;
import br.com.wallet.fraud.investigation.api.InvestigationService;
import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.api.model.RiskClassificationSource;
import br.com.wallet.decision.catalog.FraudDecisionQuestions;
import br.com.wallet.decision.composition.AssessmentStatus;
import br.com.wallet.decision.composition.DecisionComposer;
import br.com.wallet.decision.evaluator.DecisionEvaluator;
import br.com.wallet.decision.model.BooleanDecision;
import br.com.wallet.decision.model.Confidence;
import br.com.wallet.decision.model.DecisionAnswer;
import br.com.wallet.decision.model.DecisionProvenance;
import br.com.wallet.decision.model.EvaluatedQuestion;
import br.com.wallet.decision.model.EvaluationResult;
import br.com.wallet.decision.model.ScoreDecision;
import br.com.wallet.decision.model.TextDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InvestigationDispatcher Unit Tests (I-FUSION-007, REQ-FUSION-005, REQ-TYPED-017)")
class InvestigationDispatcherTest {

    private InvestigationService investigationService;
    private CheckpointRepository checkpointRepository;
    private RiskDecisionPolicy decisionPolicy;
    private ObjectMapper objectMapper;
    private DecisionEvaluator decisionEvaluator;
    private DecisionComposer decisionComposer;
    private InvestigationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        investigationService = mock(InvestigationService.class);
        checkpointRepository = mock(CheckpointRepository.class);
        decisionPolicy = new RiskDecisionPolicy();
        objectMapper = JsonMapper.builder().build();
        dispatcher = new InvestigationDispatcher(investigationService, checkpointRepository, decisionPolicy, objectMapper);
    }

    @Test
    @DisplayName("I-FUSION-007: Dispatches to Phase 0.7 InvestigationService under REVIEW")
    void shouldDispatchInvestigationUnderReview() throws Exception {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.10, 0.60, 0.40, 0.30, 0.40);
        RiskAttribution attribution = new RiskAttribution("GRAPH_INTELLIGENCE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.65, FraudDecision.REVIEW, signals, attribution);

        InvestigationEvidence evidence = new InvestigationEvidence(FraudRiskSnapshot.empty(), List.of());
        InvestigationNarrative narrative = new InvestigationNarrative(
            "High graph connection to flagged mule",
            List.of(),
            "Investigate transfer chain"
        );
        FraudInvestigationDossier mockDossier = new FraudInvestigationDossier(
            evidence,
            Optional.of(narrative),
            RiskClassification.MEDIUM,
            RiskClassificationSource.EVIDENCE_POLICY,
            List.of(RecommendedAction.MANUAL_REVIEW),
            InvestigationGenerationStatus.GENERATED
        );

        UUID expectedCheckpointId = UUID.randomUUID();
        when(investigationService.generateDossier(entityId)).thenReturn(mockDossier);
        when(checkpointRepository.saveCheckpoint(eq(entityId), any(), eq(0.65), eq("MEDIUM")))
            .thenReturn(expectedCheckpointId);

        Optional<UUID> checkpointId = dispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).contains(expectedCheckpointId);
        verify(investigationService).generateDossier(entityId);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(checkpointRepository).saveCheckpoint(eq(entityId), payloadCaptor.capture(), eq(0.65), eq("MEDIUM"));

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(root.get("decision").asText()).isEqualTo("REVIEW");
        assertThat(root.get("finalRisk").asDouble()).isEqualTo(0.65);
        assertThat(root.get("primaryDriver").asText()).isEqualTo("GRAPH_INTELLIGENCE");
        assertThat(root.get("classification").asText()).isEqualTo("MEDIUM");
        assertThat(root.get("generationStatus").asText()).isEqualTo("GENERATED");
        assertThat(root.get("executiveSummary").asText()).isEqualTo("High graph connection to flagged mule");
        assertThat(root.get("actionRationale").asText()).isEqualTo("Investigate transfer chain");
        assertThat(root.get("allowedActions").get(0).asText()).isEqualTo("MANUAL_REVIEW");
        assertThat(root.get("signals").get("graphRisk").asDouble()).isEqualTo(0.60);
    }

    @Test
    @DisplayName("I-FUSION-007: Dispatches to Phase 0.7 InvestigationService under RESTRICT")
    void shouldDispatchInvestigationUnderRestrict() throws Exception {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.20, 0.90, 0.80, 0.70, 0.85);
        RiskAttribution attribution = new RiskAttribution("GRAPH_INTELLIGENCE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.88, FraudDecision.RESTRICT, signals, attribution);

        InvestigationEvidence evidence = new InvestigationEvidence(FraudRiskSnapshot.empty(), List.of());
        InvestigationNarrative narrative = new InvestigationNarrative(
            "Critical mule network ring detected",
            List.of(),
            "Immediate outgoing restriction required"
        );
        FraudInvestigationDossier mockDossier = new FraudInvestigationDossier(
            evidence,
            Optional.of(narrative),
            RiskClassification.HIGH,
            RiskClassificationSource.EVIDENCE_POLICY,
            List.of(RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION),
            InvestigationGenerationStatus.GENERATED
        );

        UUID expectedCheckpointId = UUID.randomUUID();
        when(investigationService.generateDossier(entityId)).thenReturn(mockDossier);
        when(checkpointRepository.saveCheckpoint(eq(entityId), any(), eq(0.88), eq("HIGH")))
            .thenReturn(expectedCheckpointId);

        Optional<UUID> checkpointId = dispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).contains(expectedCheckpointId);
        verify(investigationService).generateDossier(entityId);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(checkpointRepository).saveCheckpoint(eq(entityId), payloadCaptor.capture(), eq(0.88), eq("HIGH"));

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(root.get("decision").asText()).isEqualTo("RESTRICT");
        assertThat(root.get("finalRisk").asDouble()).isEqualTo(0.88);
        assertThat(root.get("classification").asText()).isEqualTo("HIGH");
        assertThat(root.get("allowedActions").get(0).asText()).isEqualTo("TEMPORARY_OUTGOING_RESTRICTION");
    }

    @Test
    @DisplayName("I-FUSION-007: Does NOT dispatch investigation under ALLOW")
    void shouldNotDispatchInvestigationUnderAllow() {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.0, 0.10, 0.10, 0.05, 0.05);
        RiskAttribution attribution = new RiskAttribution("NONE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.10, FraudDecision.ALLOW, signals, attribution);

        Optional<UUID> checkpointId = dispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).isEmpty();
        verify(investigationService, never()).generateDossier(any());
        verify(checkpointRepository, never()).saveCheckpoint(any(), any(), any(Double.class), any());
    }

    @Test
    @DisplayName("I-FUSION-007: Does NOT dispatch investigation under HARD_BLOCK")
    void shouldNotDispatchInvestigationUnderHardBlock() {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(1.0, 0.20, 0.10, 0.05, 0.10);
        RiskAttribution attribution = new RiskAttribution("DIRECT_RULE", List.of());
        RiskFusionResult result = new RiskFusionResult(1.0, FraudDecision.HARD_BLOCK, signals, attribution);

        Optional<UUID> checkpointId = dispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).isEmpty();
        verify(investigationService, never()).generateDossier(any());
        verify(checkpointRepository, never()).saveCheckpoint(any(), any(), any(Double.class), any());
    }

    @Test
    @DisplayName("I-VEC-009 / I-FUSION-007: Gracefully persists fallback checkpoint if InvestigationService fails")
    void shouldPersistFallbackCheckpointWhenInvestigationServiceFails() throws Exception {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.15, 0.70, 0.50, 0.40, 0.45);
        RiskAttribution attribution = new RiskAttribution("GRAPH_INTELLIGENCE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.72, FraudDecision.REVIEW, signals, attribution);

        when(investigationService.generateDossier(entityId))
            .thenThrow(new RuntimeException("Local Ollama inference timeout (5000ms)"));

        UUID expectedCheckpointId = UUID.randomUUID();
        when(checkpointRepository.saveCheckpoint(eq(entityId), any(), eq(0.72), eq("MEDIUM")))
            .thenReturn(expectedCheckpointId);

        Optional<UUID> checkpointId = dispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).contains(expectedCheckpointId);
        verify(investigationService).generateDossier(entityId);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(checkpointRepository).saveCheckpoint(eq(entityId), payloadCaptor.capture(), eq(0.72), eq("MEDIUM"));

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(root.get("decision").asText()).isEqualTo("REVIEW");
        assertThat(root.get("classification").asText()).isEqualTo("MEDIUM");
        assertThat(root.get("generationStatus").asText()).isEqualTo("INFERENCE_UNAVAILABLE");
        assertThat(root.get("fallbackReason").asText()).isEqualTo("DOSSIER_GENERATION_FAILED");
    }

    @Test
    @DisplayName("REQ-TYPED-017: Enriches checkpoint with typed AI compound assessment without mutating finalRisk")
    void shouldEnrichCheckpointWithTypedAICompoundAssessment() throws Exception {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.20, 0.75, 0.60, 0.50, 0.70);
        RiskAttribution attribution = new RiskAttribution("GRAPH_INTELLIGENCE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.78, FraudDecision.RESTRICT, signals, attribution);

        decisionEvaluator = mock(DecisionEvaluator.class);
        decisionComposer = new DecisionComposer();
        InvestigationDispatcher enrichedDispatcher = new InvestigationDispatcher(
            investigationService, checkpointRepository, decisionPolicy, objectMapper,
            decisionEvaluator, decisionComposer
        );

        InvestigationEvidence evidence = new InvestigationEvidence(FraudRiskSnapshot.empty(), List.of());
        FraudInvestigationDossier mockDossier = new FraudInvestigationDossier(
            evidence,
            Optional.empty(),
            RiskClassification.HIGH,
            RiskClassificationSource.EVIDENCE_POLICY,
            List.of(RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION),
            InvestigationGenerationStatus.GENERATED
        );
        when(investigationService.generateDossier(entityId)).thenReturn(mockDossier);

        String expectedEvalId = "eval-" + UUID.randomUUID();
        DecisionProvenance provenance = new DecisionProvenance("smollm2:360m", "v1.0", Instant.now(), 50L);
        EvaluationResult mockEvalResult = new EvaluationResult(
            expectedEvalId,
            entityId.toString(),
            List.of(
                new EvaluatedQuestion<>(
                    FraudDecisionQuestions.BEHAVIOR_ANOMALY,
                    new DecisionAnswer<>(new BooleanDecision(true, "High anomaly observed"), Confidence.HIGH, List.of(), provenance)
                ),
                new EvaluatedQuestion<>(
                    FraudDecisionQuestions.SUSPECTED_MULE_RING,
                    new DecisionAnswer<>(new BooleanDecision(false, "No ring topology"), Confidence.HIGH, List.of(), provenance)
                ),
                new EvaluatedQuestion<>(
                    FraudDecisionQuestions.ANOMALOUS_CASH_OUT,
                    new DecisionAnswer<>(new ScoreDecision(new BigDecimal("0.85"), "Rapid cashout risk"), Confidence.HIGH, List.of(), provenance)
                ),
                new EvaluatedQuestion<>(
                    FraudDecisionQuestions.INVESTIGATION_SUMMARY,
                    new DecisionAnswer<>(new TextDecision("Mule risk identified with rapid cashout profile"), Confidence.HIGH, List.of(), provenance)
                )
            ),
            Instant.now()
        );

        when(decisionEvaluator.evaluate(eq(entityId.toString()), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(mockEvalResult));

        UUID expectedCheckpointId = UUID.randomUUID();
        when(checkpointRepository.saveCheckpoint(eq(entityId), any(), eq(0.78), eq("HIGH")))
            .thenReturn(expectedCheckpointId);

        Optional<UUID> checkpointId = enrichedDispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).contains(expectedCheckpointId);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(checkpointRepository).saveCheckpoint(eq(entityId), payloadCaptor.capture(), eq(0.78), eq("HIGH"));

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(root.get("decision").asText()).isEqualTo("RESTRICT");
        assertThat(root.get("finalRisk").asDouble()).isEqualTo(0.78);
        assertThat(root.get("evaluationId").asText()).isEqualTo(expectedEvalId);
        assertThat(root.get("evidenceHash").asText()).hasSize(64);

        JsonNode compound = root.get("compoundAssessment");
        assertThat(compound).isNotNull();
        assertThat(compound.get("status").asText()).isEqualTo(AssessmentStatus.VERIFIED.name());
        assertThat(compound.get("triggeredSignals").toString()).contains("BEHAVIOR_ANOMALY");
        assertThat(compound.get("explanation").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("REQ-TYPED-017 / I-TYPED-005: Gracefully handles decision evaluator failure without breaking dispatch")
    void shouldPersistGracefullyWhenDecisionEvaluatorFails() throws Exception {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.10, 0.65, 0.40, 0.30, 0.40);
        RiskAttribution attribution = new RiskAttribution("GRAPH_INTELLIGENCE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.68, FraudDecision.REVIEW, signals, attribution);

        decisionEvaluator = mock(DecisionEvaluator.class);
        decisionComposer = new DecisionComposer();
        InvestigationDispatcher enrichedDispatcher = new InvestigationDispatcher(
            investigationService, checkpointRepository, decisionPolicy, objectMapper,
            decisionEvaluator, decisionComposer
        );

        when(investigationService.generateDossier(entityId)).thenReturn(new FraudInvestigationDossier(
            new InvestigationEvidence(FraudRiskSnapshot.empty(), List.of()),
            Optional.empty(),
            RiskClassification.MEDIUM,
            RiskClassificationSource.EVIDENCE_POLICY,
            List.of(),
            InvestigationGenerationStatus.GENERATED
        ));

        CompletableFuture<EvaluationResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Inference timeout"));
        when(decisionEvaluator.evaluate(eq(entityId.toString()), any(), any())).thenReturn(failedFuture);

        UUID expectedCheckpointId = UUID.randomUUID();
        when(checkpointRepository.saveCheckpoint(eq(entityId), any(), eq(0.68), eq("MEDIUM")))
            .thenReturn(expectedCheckpointId);

        Optional<UUID> checkpointId = enrichedDispatcher.dispatchIfRequired(entityId, result);

        assertThat(checkpointId).contains(expectedCheckpointId);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(checkpointRepository).saveCheckpoint(eq(entityId), payloadCaptor.capture(), eq(0.68), eq("MEDIUM"));

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(root.get("decision").asText()).isEqualTo("REVIEW");
        assertThat(root.get("finalRisk").asDouble()).isEqualTo(0.68);
        assertThat(root.get("typedAssessmentStatus").asText()).isEqualTo("UNAVAILABLE");
    }
}
