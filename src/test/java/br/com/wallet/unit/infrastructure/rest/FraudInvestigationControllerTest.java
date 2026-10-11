package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.fraud.investigation.api.InvestigationService;
import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.ClaimType;
import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationClaim;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.api.model.RiskClassificationSource;
import br.com.wallet.fraud.investigation.api.model.TypedEvaluationResponse;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.infrastructure.rest.controller.FraudInvestigationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudInvestigationController.class)
@DisplayName("FraudInvestigationController Unit Tests (REST Endpoints)")
class FraudInvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationService investigationService;

    @Test
    @DisplayName("GET /api/v1/fraud/intelligence/investigation/dossier/{entityId} -> should return 200 OK with dossier")
    void shouldGenerateDossierSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();

        InvestigationEvidence evidence = new InvestigationEvidence(
            new FraudRiskSnapshot(0.2, 0.8, 0.4, 0.85, 2.1, "MONEY_MULE_RAPID_DRAIN", 0.9),
            List.of(new AtomicEvidenceItem("GRAPH-001", "CLUSTER", "USER", Map.of("count", 3)))
        );

        InvestigationNarrative narrative = new InvestigationNarrative(
            "Executive mule summary.",
            List.of(new InvestigationClaim(ClaimType.ARCHETYPE_SIMILARITY, "Mule claim.", List.of("GRAPH-001"))),
            "Action rationale."
        );

        FraudInvestigationDossier dossier = new FraudInvestigationDossier(
            evidence,
            Optional.of(narrative),
            RiskClassification.HIGH,
            RiskClassificationSource.EVIDENCE_POLICY,
            List.of(RecommendedAction.MANUAL_REVIEW, RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION),
            InvestigationGenerationStatus.GENERATED
        );

        when(investigationService.generateDossier(eq(entityId), any(InferenceCapability.class))).thenReturn(dossier);

        mockMvc.perform(get("/api/v1/fraud/intelligence/investigation/dossier/{entityId}", entityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.classification").value("HIGH"))
            .andExpect(jsonPath("$.status").value("GENERATED"))
            .andExpect(jsonPath("$.allowedActions[0]").value("MANUAL_REVIEW"))
            .andExpect(jsonPath("$.narrative.executiveSummary").value("Executive mule summary."));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/investigation/{entityId}/evaluate -> should evaluate typed decisions (REQ-TYPED-018)")
    void shouldEvaluateTypedDecisionsSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();
        String evalId = "eval-" + UUID.randomUUID();

        TypedEvaluationResponse response = new TypedEvaluationResponse(
            entityId,
            evalId,
            "hash-12345",
            List.of(
                new TypedEvaluationResponse.QuestionOutcomeDto(
                    "Q-FRAUD-001",
                    "behavior_anomaly",
                    "BooleanDecision",
                    "ANSWER",
                    true,
                    "HIGH",
                    "Anomaly verified",
                    null,
                    "smollm2:360m",
                    40L
                ),
                new TypedEvaluationResponse.QuestionOutcomeDto(
                    "Q-FRAUD-002",
                    "anomalous_cash_out",
                    "ScoreDecision",
                    "ANSWER",
                    new BigDecimal("0.72"),
                    "HIGH",
                    "Score verified",
                    null,
                    "smollm2:360m",
                    40L
                )
            ),
            new TypedEvaluationResponse.CompoundAssessmentDto(
                "VERIFIED",
                Set.of("BEHAVIOR_ANOMALY"),
                List.of(),
                "Verified behavioral anomaly with grounded evidence"
            )
        );

        when(investigationService.evaluateDecisions(entityId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/fraud/intelligence/investigation/{entityId}/evaluate", entityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.evaluationId").value(evalId))
            .andExpect(jsonPath("$.evidenceHash").value("hash-12345"))
            .andExpect(jsonPath("$.outcomes[0].questionId").value("Q-FRAUD-001"))
            .andExpect(jsonPath("$.outcomes[0].value").value(true))
            .andExpect(jsonPath("$.outcomes[0].confidence").value("HIGH"))
            .andExpect(jsonPath("$.compoundAssessment.status").value("VERIFIED"))
            .andExpect(jsonPath("$.compoundAssessment.triggeredSignals[0]").value("BEHAVIOR_ANOMALY"));
    }

    @Test
    @DisplayName("GET /api/v1/fraud/intelligence/investigation/{entityId}/decisions -> should return typed decisions (REQ-TYPED-018)")
    void shouldGetTypedDecisionsSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();
        String evalId = "eval-" + UUID.randomUUID();

        TypedEvaluationResponse response = new TypedEvaluationResponse(
            entityId,
            evalId,
            "hash-67890",
            List.of(
                new TypedEvaluationResponse.QuestionOutcomeDto(
                    "Q-FRAUD-001",
                    "behavior_anomaly",
                    "BooleanDecision",
                    "ANSWER",
                    false,
                    "HIGH",
                    "Normal activity",
                    null,
                    "smollm2:360m",
                    25L
                )
            ),
            new TypedEvaluationResponse.CompoundAssessmentDto(
                "VERIFIED",
                Set.of(),
                List.of(),
                "No anomalous patterns detected"
            )
        );

        when(investigationService.getDecisions(entityId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/fraud/intelligence/investigation/{entityId}/decisions", entityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.evaluationId").value(evalId))
            .andExpect(jsonPath("$.compoundAssessment.status").value("VERIFIED"));
    }
}
