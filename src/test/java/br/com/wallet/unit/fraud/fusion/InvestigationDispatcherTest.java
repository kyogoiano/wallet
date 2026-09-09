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
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.api.model.RiskClassificationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InvestigationDispatcher Unit Tests (I-FUSION-007, REQ-FUSION-005)")
class InvestigationDispatcherTest {

    private InvestigationService investigationService;
    private CheckpointRepository checkpointRepository;
    private RiskDecisionPolicy decisionPolicy;
    private ObjectMapper objectMapper;
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

        InvestigationEvidence mockEvidence = mock(InvestigationEvidence.class);
        InvestigationNarrative narrative = new InvestigationNarrative(
            "High graph connection to flagged mule",
            List.of(),
            "Investigate transfer chain"
        );
        FraudInvestigationDossier mockDossier = new FraudInvestigationDossier(
            mockEvidence,
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

        InvestigationEvidence mockEvidence = mock(InvestigationEvidence.class);
        InvestigationNarrative narrative = new InvestigationNarrative(
            "Critical mule network ring detected",
            List.of(),
            "Immediate outgoing restriction required"
        );
        FraudInvestigationDossier mockDossier = new FraudInvestigationDossier(
            mockEvidence,
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
}
