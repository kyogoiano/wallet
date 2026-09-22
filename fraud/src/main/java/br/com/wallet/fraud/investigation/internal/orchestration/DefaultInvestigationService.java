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
import br.com.wallet.decision.catalog.FraudDecisionQuestions;
import br.com.wallet.decision.composition.CompositionPolicy;
import br.com.wallet.decision.composition.CompoundRiskAssessment;
import br.com.wallet.decision.composition.DecisionComposer;
import br.com.wallet.decision.evaluator.DecisionEvaluator;
import br.com.wallet.decision.model.BooleanDecision;
import br.com.wallet.decision.model.DecisionAnswer;
import br.com.wallet.decision.model.DecisionEvidence;
import br.com.wallet.decision.model.DecisionUnavailable;
import br.com.wallet.decision.model.EvaluationResult;
import br.com.wallet.decision.model.ScoreDecision;
import br.com.wallet.decision.model.TextDecision;
import br.com.wallet.fraud.investigation.api.model.TypedEvaluationResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private final @Nullable DecisionEvaluator decisionEvaluator;
    private final DecisionComposer decisionComposer;

    public DefaultInvestigationService(
        @NonNull final InvestigationContextBuilder contextBuilder,
        @NonNull final RiskClassificationPolicy classificationPolicy,
        @NonNull final RecommendedActionPolicy actionPolicy,
        @NonNull final PiiMaskingService piiMaskingService,
        @NonNull final LocalInferenceClient inferenceClient,
        @NonNull final ClaimGroundingValidator groundingValidator
    ) {
        this(contextBuilder, classificationPolicy, actionPolicy, piiMaskingService, inferenceClient, groundingValidator, null, new DecisionComposer());
    }

    @Autowired
    public DefaultInvestigationService(
        @NonNull final InvestigationContextBuilder contextBuilder,
        @NonNull final RiskClassificationPolicy classificationPolicy,
        @NonNull final RecommendedActionPolicy actionPolicy,
        @NonNull final PiiMaskingService piiMaskingService,
        @NonNull final LocalInferenceClient inferenceClient,
        @NonNull final ClaimGroundingValidator groundingValidator,
        @Autowired(required = false) @Nullable final DecisionEvaluator decisionEvaluator,
        @Autowired(required = false) @Nullable final DecisionComposer decisionComposer
    ) {
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder cannot be null");
        this.classificationPolicy = Objects.requireNonNull(classificationPolicy, "classificationPolicy cannot be null");
        this.actionPolicy = Objects.requireNonNull(actionPolicy, "actionPolicy cannot be null");
        this.piiMaskingService = Objects.requireNonNull(piiMaskingService, "piiMaskingService cannot be null");
        this.inferenceClient = Objects.requireNonNull(inferenceClient, "inferenceClient cannot be null");
        this.groundingValidator = Objects.requireNonNull(groundingValidator, "groundingValidator cannot be null");
        this.decisionEvaluator = decisionEvaluator;
        this.decisionComposer = decisionComposer != null ? decisionComposer : new DecisionComposer();
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

    @Override
    @NonNull
    public TypedEvaluationResponse evaluateDecisions(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        return performTypedEvaluation(entityId);
    }

    @Override
    @NonNull
    public TypedEvaluationResponse getDecisions(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        return performTypedEvaluation(entityId);
    }

    private TypedEvaluationResponse performTypedEvaluation(final UUID entityId) {
        FraudInvestigationDossier dossier = generateDossier(entityId);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("entityId", entityId.toString());
        facts.put("classification", dossier.classification().name());
        facts.put("generationStatus", dossier.status().name());
        var snap = dossier.evidence().risks();
        facts.put("directRisk", String.format(Locale.ROOT, "%.2f", snap.directRisk()));
        facts.put("graphRisk", String.format(Locale.ROOT, "%.2f", snap.graphRisk()));
        facts.put("propagatedRisk", String.format(Locale.ROOT, "%.2f", snap.propagatedRisk()));
        facts.put("behavioralRisk", String.format(Locale.ROOT, "%.2f", snap.behavioralRisk()));
        facts.put("archetypeSimilarity", String.format(Locale.ROOT, "%.2f", snap.archetypeSimilarity()));
        facts.put("topArchetype", snap.topArchetype().name());

        DecisionEvidence evidence = DecisionEvidence.of(
            "investigation_snapshot",
            "Snapshot of investigation signals and facts",
            facts
        );

        EvaluationResult evalResult;
        if (decisionEvaluator != null) {
            try {
                evalResult = decisionEvaluator.evaluate(
                    entityId.toString(),
                    FraudDecisionQuestions.allStandardQuestions(),
                    Map.of("investigation_snapshot", evidence)
                ).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Typed decision evaluation failed: " + e.getMessage(), e);
            }
        } else {
            evalResult = new EvaluationResult(
                "eval-fallback-" + UUID.randomUUID(),
                entityId.toString(),
                List.of(),
                Instant.now()
            );
        }

        CompoundRiskAssessment assessment = decisionComposer.compose(
            evalResult,
            CompositionPolicy.DEGRADE_TO_UNVERIFIED,
            Set.of(FraudDecisionQuestions.BEHAVIOR_ANOMALY)
        );

        List<TypedEvaluationResponse.QuestionOutcomeDto> outcomeDtos = evalResult.questions().stream()
            .map(eq -> {
                var q = eq.question();
                var outcome = eq.outcome();
                if (outcome instanceof DecisionAnswer<?> ans) {
                    Object rawVal;
                    String explanation;
                    if (ans.value() instanceof BooleanDecision(boolean value, String rationale)) {
                        rawVal = value;
                        explanation = rationale;
                    } else if (ans.value() instanceof ScoreDecision(java.math.BigDecimal score, String rationale)) {
                        rawVal = score;
                        explanation = rationale;
                    } else if (ans.value() instanceof TextDecision(String summary)) {
                        rawVal = summary;
                        explanation = summary;
                    } else {
                        rawVal = ans.value().toString();
                        explanation = "";
                    }
                    return new TypedEvaluationResponse.QuestionOutcomeDto(
                        q.questionId(),
                        q.questionKey(),
                        q.valueType().getSimpleName(),
                        "ANSWER",
                        rawVal,
                        ans.confidence().name(),
                        explanation,
                        null,
                        ans.provenance().modelIdentifier(),
                        ans.provenance().latencyMs()
                    );
                } else if (outcome instanceof DecisionUnavailable<?> unavail) {
                    return new TypedEvaluationResponse.QuestionOutcomeDto(
                        q.questionId(),
                        q.questionKey(),
                        q.valueType().getSimpleName(),
                        "UNAVAILABLE",
                        null,
                        null,
                        null,
                        unavail.reason().name() + ": " + unavail.diagnosticMessage(),
                        unavail.provenance().modelIdentifier(),
                        unavail.provenance().latencyMs()
                    );
                }
                return new TypedEvaluationResponse.QuestionOutcomeDto(
                    q.questionId(),
                    q.questionKey(),
                    q.valueType().getSimpleName(),
                    "UNKNOWN",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                );
            })
            .toList();

        TypedEvaluationResponse.CompoundAssessmentDto compoundDto = new TypedEvaluationResponse.CompoundAssessmentDto(
            assessment.status().name(),
            assessment.triggeredSignals(),
            assessment.unavailables().stream().map(u -> u.reason().name() + ": " + u.diagnosticMessage()).toList(),
            assessment.explanation()
        );

        return new TypedEvaluationResponse(
            entityId,
            evalResult.evaluationId(),
            evidence.contentHash(),
            outcomeDtos,
            compoundDto
        );
    }
}

