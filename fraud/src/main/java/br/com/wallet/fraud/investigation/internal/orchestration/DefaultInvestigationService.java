package br.com.wallet.fraud.investigation.internal.orchestration;

import br.com.wallet.fraud.investigation.api.InvestigationService;
import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.api.model.RiskClassificationSource;
import br.com.wallet.fraud.investigation.internal.evidence.InvestigationContextBuilder;
import br.com.wallet.fraud.investigation.internal.grounding.ClaimGroundingValidator;
import br.com.wallet.fraud.investigation.internal.policy.RecommendedActionPolicy;
import br.com.wallet.fraud.investigation.internal.policy.RiskClassificationPolicy;
import br.com.wallet.fraud.investigation.internal.sanitization.PiiMaskingService;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.fraud.investigation.spi.InferenceModelProfile;
import br.com.wallet.fraud.investigation.spi.LocalInferenceClient;
import br.com.wallet.fraud.investigation.spi.StructuredInferenceRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator implementing InvestigationService: builds deterministic evidence, derives deterministic
 * actions and classifications, sanitizes PII, invokes local inference, and enforces graceful degradation (I-VEC-009).
 */
@Service
public class DefaultInvestigationService implements InvestigationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvestigationService.class);

    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are a financial fraud investigation synthesizer and analyst copilot.
        Explain deterministic evidence facts clearly. You MUST NOT decide risk scores or allowable actions.
        Every claim you make must cite a valid evidence ID.
        """;

    private static final String JSON_SCHEMA_CONSTRAINT = """
        {"type": "object", "properties": {"executiveSummary": {"type": "string"}, "claims": {"type": "array"}, "actionRationale": {"type": "string"}}, "required": ["executiveSummary", "claims", "actionRationale"]}
        """;

    private final InvestigationContextBuilder contextBuilder;
    private final RiskClassificationPolicy classificationPolicy;
    private final RecommendedActionPolicy actionPolicy;
    private final PiiMaskingService piiMaskingService;
    private final LocalInferenceClient inferenceClient;
    private final ClaimGroundingValidator groundingValidator;

    public DefaultInvestigationService(
        @NonNull final InvestigationContextBuilder contextBuilder,
        @NonNull final RiskClassificationPolicy classificationPolicy,
        @NonNull final RecommendedActionPolicy actionPolicy,
        @NonNull final PiiMaskingService piiMaskingService,
        @NonNull final LocalInferenceClient inferenceClient,
        @NonNull final ClaimGroundingValidator groundingValidator
    ) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder cannot be null");
        this.classificationPolicy = Objects.requireNonNull(classificationPolicy, "classificationPolicy cannot be null");
        this.actionPolicy = Objects.requireNonNull(actionPolicy, "actionPolicy cannot be null");
        this.piiMaskingService = Objects.requireNonNull(piiMaskingService, "piiMaskingService cannot be null");
        this.inferenceClient = Objects.requireNonNull(inferenceClient, "inferenceClient cannot be null");
        this.groundingValidator = Objects.requireNonNull(groundingValidator, "groundingValidator cannot be null");
    }

    @Override
    @NonNull
    public FraudInvestigationDossier generateDossier(@NonNull final UUID entityId) {
        return generateDossier(entityId, InferenceCapability.BALANCED);
    }

    @Override
    @NonNull
    public FraudInvestigationDossier generateDossier(
        @NonNull final UUID entityId,
        @NonNull final InferenceCapability capability
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(capability, "capability cannot be null");

        // 1. Build deterministic facts
        InvestigationEvidence evidence = contextBuilder.buildEvidence(entityId);

        // 2. Evaluate deterministic severity and actions
        RiskClassification classification = classificationPolicy.classify(evidence.risks());
        List<RecommendedAction> allowedActions = actionPolicy.determineAllowedActions(classification, evidence.risks());

        // 3. Apply Hard Sanitization Boundary before model invocation
        SanitizedInferenceContext sanitizedContext = piiMaskingService.sanitize(
            entityId, evidence, classification, allowedActions
        );

        // 4. Select profile
        InferenceModelProfile profile = switch (capability) {
            case FAST -> InferenceModelProfile.fast();
            case BALANCED -> InferenceModelProfile.balanced();
            case HIGH_QUALITY -> InferenceModelProfile.highQuality();
        };

        StructuredInferenceRequest request = new StructuredInferenceRequest(
            sanitizedContext,
            capability,
            profile,
            DEFAULT_SYSTEM_PROMPT,
            JSON_SCHEMA_CONSTRAINT
        );

        // 5. Invoke Local Inference with Graceful Degradation (I-VEC-009)
        try {
            Optional<InvestigationNarrative> narrativeOpt = inferenceClient.generateNarrative(request);

            if (narrativeOpt.isPresent()) {
                InvestigationNarrative narrative = narrativeOpt.get();
                if (groundingValidator.isValid(narrative, evidence)) {
                    return new FraudInvestigationDossier(
                        evidence,
                        Optional.of(narrative),
                        classification,
                        RiskClassificationSource.EVIDENCE_POLICY,
                        allowedActions,
                        InvestigationGenerationStatus.GENERATED
                    );
                } else {
                    log.warn("Narrative failed grounding validation for entity: {}", entityId);
                    return new FraudInvestigationDossier(
                        evidence,
                        Optional.empty(),
                        classification,
                        RiskClassificationSource.EVIDENCE_POLICY,
                        allowedActions,
                        InvestigationGenerationStatus.VALIDATION_FAILED
                    );
                }
            }
        } catch (Exception e) {
            log.warn("Inference failed for entity {}: {}, applying graceful degradation", entityId, e.getMessage());
        }

        // Graceful degradation: inference unavailable, deterministic evidence preserved
        return new FraudInvestigationDossier(
            evidence,
            Optional.empty(),
            classification,
            RiskClassificationSource.EVIDENCE_POLICY,
            allowedActions,
            InvestigationGenerationStatus.INFERENCE_UNAVAILABLE
        );
    }
}
