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
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.infrastructure.rest.controller.FraudInvestigationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
