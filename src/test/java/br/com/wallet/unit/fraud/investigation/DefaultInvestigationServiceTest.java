package br.com.wallet.unit.fraud.investigation;

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
import br.com.wallet.fraud.investigation.internal.evidence.InvestigationContextBuilder;
import br.com.wallet.fraud.investigation.internal.grounding.ClaimGroundingValidator;
import br.com.wallet.fraud.investigation.internal.orchestration.DefaultInvestigationService;
import br.com.wallet.fraud.investigation.internal.policy.RecommendedActionPolicy;
import br.com.wallet.fraud.investigation.internal.policy.RiskClassificationPolicy;
import br.com.wallet.fraud.investigation.internal.sanitization.PiiMaskingService;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.fraud.investigation.spi.LocalInferenceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultInvestigationService Unit Tests (Dossier Orchestration & Graceful Degradation I-VEC-009)")
class DefaultInvestigationServiceTest {

    @Mock
    private InvestigationContextBuilder contextBuilder;

    @Mock
    private RiskClassificationPolicy classificationPolicy;

    @Mock
    private RecommendedActionPolicy actionPolicy;

    @Mock
    private PiiMaskingService piiMaskingService;

    @Mock
    private LocalInferenceClient inferenceClient;

    @Mock
    private ClaimGroundingValidator groundingValidator;

    private DefaultInvestigationService service;

    private UUID entityId;
    private InvestigationEvidence evidence;

    @BeforeEach
    void setUp() {
        service = new DefaultInvestigationService(
            contextBuilder, classificationPolicy, actionPolicy, piiMaskingService, inferenceClient, groundingValidator
        );

        entityId = UUID.randomUUID();
        evidence = new InvestigationEvidence(
            new FraudRiskSnapshot(0.3, 0.8, 0.5, 0.85, 2.0, "MONEY_MULE_RAPID_DRAIN", 0.9),
            List.of(new AtomicEvidenceItem("GRAPH-001", "CLUSTER", "USER", Map.of("count", 3)))
        );

        when(contextBuilder.buildEvidence(entityId)).thenReturn(evidence);
        when(classificationPolicy.classify(any())).thenReturn(RiskClassification.HIGH);
        when(actionPolicy.determineAllowedActions(any(), any())).thenReturn(List.of(RecommendedAction.MANUAL_REVIEW));
        when(piiMaskingService.sanitize(any(), any(), any(), any())).thenReturn(
            new SanitizedInferenceContext("MASK_USER_TARGET", evidence.risks(), evidence.evidenceItems(), RiskClassification.HIGH, List.of(RecommendedAction.MANUAL_REVIEW))
        );
    }

    @Test
    @DisplayName("REQ-VEC-006: Should assemble complete dossier with GENERATED status when inference succeeds")
    void shouldAssembleDossierSuccessfully() {
        InvestigationNarrative narrative = new InvestigationNarrative(
            "High confidence mule pattern.",
            List.of(new InvestigationClaim(ClaimType.ARCHETYPE_SIMILARITY, "Money mule detected.", List.of("GRAPH-001"))),
            "Manual review recommended."
        );

        when(inferenceClient.generateNarrative(any())).thenReturn(Optional.of(narrative));
        when(groundingValidator.isValid(narrative, evidence)).thenReturn(true);

        FraudInvestigationDossier dossier = service.generateDossier(entityId, InferenceCapability.BALANCED);

        assertThat(dossier.status()).isEqualTo(InvestigationGenerationStatus.GENERATED);
        assertThat(dossier.classification()).isEqualTo(RiskClassification.HIGH);
        assertThat(dossier.classificationSource()).isEqualTo(RiskClassificationSource.EVIDENCE_POLICY);
        assertThat(dossier.narrative()).isPresent();
        assertThat(dossier.narrative().get().executiveSummary()).isEqualTo("High confidence mule pattern.");
    }

    @Test
    @DisplayName("I-VEC-009: Graceful Degradation should return INFERENCE_UNAVAILABLE preserving deterministic evidence")
    void shouldGracefullyDegradeWhenInferenceFails() {
        when(inferenceClient.generateNarrative(any())).thenThrow(new RuntimeException("Inference server offline"));

        FraudInvestigationDossier dossier = service.generateDossier(entityId, InferenceCapability.BALANCED);

        assertThat(dossier.status()).isEqualTo(InvestigationGenerationStatus.INFERENCE_UNAVAILABLE);
        assertThat(dossier.classification()).isEqualTo(RiskClassification.HIGH);
        assertThat(dossier.allowedActions()).contains(RecommendedAction.MANUAL_REVIEW);
        assertThat(dossier.evidence()).isEqualTo(evidence);
        assertThat(dossier.narrative()).isEmpty();
    }
}
