package br.com.wallet.fraud.fusion.internal.orchestration;

import br.com.wallet.decision.catalog.FraudDecisionQuestions;
import br.com.wallet.decision.composition.CompositionPolicy;
import br.com.wallet.decision.composition.CompoundRiskAssessment;
import br.com.wallet.decision.composition.DecisionComposer;
import br.com.wallet.decision.evaluator.DecisionEvaluator;
import br.com.wallet.decision.model.DecisionEvidence;
import br.com.wallet.decision.model.EvaluationResult;
import br.com.wallet.fraud.fusion.api.CheckpointRepository;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.internal.policy.RiskDecisionPolicy;
import br.com.wallet.fraud.investigation.api.InvestigationService;
import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches investigations to Phase 0.7 InvestigationService under REVIEW and RESTRICT decisions (I-FUSION-007).
 * Enriches checkpoints with strongly typed AI decisions and CompoundRiskAssessment without mutating deterministic fusion math (REQ-TYPED-017).
 */
@Component
public class InvestigationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvestigationDispatcher.class);

    private final InvestigationService investigationService;
    private final CheckpointRepository checkpointRepository;
    private final RiskDecisionPolicy decisionPolicy;
    private final ObjectMapper objectMapper;
    private final @Nullable DecisionEvaluator decisionEvaluator;
    private final DecisionComposer decisionComposer;

    public InvestigationDispatcher(
        @NonNull final InvestigationService investigationService,
        @NonNull final CheckpointRepository checkpointRepository,
        @NonNull final RiskDecisionPolicy decisionPolicy,
        @NonNull final ObjectMapper objectMapper
    ) {
        this(investigationService, checkpointRepository, decisionPolicy, objectMapper, null, new DecisionComposer());
    }

    @Autowired
    public InvestigationDispatcher(
        @NonNull final InvestigationService investigationService,
        @NonNull final CheckpointRepository checkpointRepository,
        @NonNull final RiskDecisionPolicy decisionPolicy,
        @NonNull final ObjectMapper objectMapper,
        @Nullable final DecisionEvaluator decisionEvaluator,
        @NonNull final DecisionComposer decisionComposer
    ) {
        this.investigationService = Objects.requireNonNull(investigationService, "investigationService cannot be null");
        this.checkpointRepository = Objects.requireNonNull(checkpointRepository, "checkpointRepository cannot be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.decisionEvaluator = decisionEvaluator;
        this.decisionComposer = Objects.requireNonNull(decisionComposer, "decisionComposer cannot be null");
    }

    @NonNull
    public Optional<UUID> dispatchIfRequired(@NonNull final UUID entityId, @NonNull final RiskFusionResult fusionResult) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(fusionResult, "fusionResult cannot be null");

        if (!decisionPolicy.shouldTriggerInvestigation(fusionResult.decision())) {
            log.debug("Investigation not required for entity {} with decision {}", entityId, fusionResult.decision());
            return Optional.empty();
        }

        log.info("Triggering investigation for entity {} under decision {}", entityId, fusionResult.decision());

        FraudInvestigationDossier dossier = null;
        try {
            dossier = investigationService.generateDossier(entityId);
        } catch (Exception e) {
            log.warn("Failed to generate complete investigation dossier for entity {}: {}, applying graceful fallback",
                entityId, e.getMessage(), e);
        }

        String classification;
        String generationStatus;
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("decision", fusionResult.decision().name());
        payloadMap.put("finalRisk", fusionResult.finalRisk());
        payloadMap.put("primaryDriver", fusionResult.attribution().primaryDriver());

        Map<String, Object> signalsMap = new LinkedHashMap<>();
        signalsMap.put("directRisk", fusionResult.signals().directRisk());
        signalsMap.put("graphRisk", fusionResult.signals().graphRisk());
        signalsMap.put("propagatedRisk", fusionResult.signals().propagatedRisk());
        signalsMap.put("behavioralRisk", fusionResult.signals().behavioralRisk());
        signalsMap.put("mlRisk", fusionResult.signals().mlRisk());
        payloadMap.put("signals", signalsMap);

        if (dossier != null) {
            classification = dossier.classification().name();
            generationStatus = dossier.status().name();
            payloadMap.put("classification", classification);
            payloadMap.put("generationStatus", generationStatus);
            dossier.narrative().ifPresent(narrative -> {
                payloadMap.put("executiveSummary", narrative.executiveSummary());
                payloadMap.put("actionRationale", narrative.actionRationale());
            });
            if (!dossier.allowedActions().isEmpty()) {
                payloadMap.put("allowedActions", dossier.allowedActions().stream().map(Enum::name).toList());
            }
        } else {
            classification = fusionResult.decision() == FraudDecision.RESTRICT
                ? RiskClassification.HIGH.name()
                : RiskClassification.MEDIUM.name();
            generationStatus = InvestigationGenerationStatus.INFERENCE_UNAVAILABLE.name();
            payloadMap.put("classification", classification);
            payloadMap.put("generationStatus", generationStatus);
            payloadMap.put("fallbackReason", "DOSSIER_GENERATION_FAILED");
        }

        // Nearline Typed AI Decision Algebra Evaluation (REQ-TYPED-017)
        if (decisionEvaluator != null) {
            try {
                Map<String, Object> facts = new LinkedHashMap<>();
                facts.put("entityId", entityId.toString());
                facts.put("decision", fusionResult.decision().name());
                facts.put("finalRisk", String.format(Locale.ROOT, "%.2f", fusionResult.finalRisk()));
                facts.put("primaryDriver", fusionResult.attribution().primaryDriver());
                facts.put("directRisk", String.format(Locale.ROOT, "%.2f", fusionResult.signals().directRisk()));
                facts.put("graphRisk", String.format(Locale.ROOT, "%.2f", fusionResult.signals().graphRisk()));
                facts.put("propagatedRisk", String.format(Locale.ROOT, "%.2f", fusionResult.signals().propagatedRisk()));
                facts.put("behavioralRisk", String.format(Locale.ROOT, "%.2f", fusionResult.signals().behavioralRisk()));
                if (fusionResult.signals().mlRisk() instanceof br.com.wallet.fraud.fusion.api.model.MlRiskResult.Available avail) {
                    facts.put("mlRisk", String.format(Locale.ROOT, "%.2f", avail.score()));
                } else {
                    facts.put("mlRisk", "UNAVAILABLE");
                }

                DecisionEvidence evidence = DecisionEvidence.of(
                    "fusion_summary",
                    "Deterministic fusion signals and attribution snapshot",
                    facts
                );

                var future = decisionEvaluator.evaluate(
                    entityId.toString(),
                    FraudDecisionQuestions.allStandardQuestions(),
                    Map.of("fusion_summary", evidence)
                );
                if (future != null) {
                    EvaluationResult evalResult = future.get(3, TimeUnit.SECONDS);

                    CompoundRiskAssessment assessment = decisionComposer.compose(
                        evalResult,
                        CompositionPolicy.DEGRADE_TO_UNVERIFIED,
                        Set.of(FraudDecisionQuestions.BEHAVIOR_ANOMALY)
                    );

                    payloadMap.put("evaluationId", evalResult.evaluationId());
                    payloadMap.put("evidenceHash", evidence.contentHash());

                    Map<String, Object> assessmentMap = new LinkedHashMap<>();
                    assessmentMap.put("status", assessment.status().name());
                    assessmentMap.put("triggeredSignals", assessment.triggeredSignals());
                    assessmentMap.put("explanation", assessment.explanation());
                    assessmentMap.put("unavailablesCount", assessment.unavailables().size());
                    payloadMap.put("compoundAssessment", assessmentMap);
                }
            } catch (Exception e) {
                log.warn("Nearline typed decision evaluation failed or timed out for entity {}: {}, applying graceful fallback",
                    entityId, e.getMessage());
                payloadMap.put("typedAssessmentStatus", "UNAVAILABLE");
            }
        }

        String statePayload;
        try {
            statePayload = objectMapper.writeValueAsString(payloadMap);
        } catch (JacksonException e) {
            log.error("Failed to serialize checkpoint state payload for entity {}", entityId, e);
            statePayload = "{\"decision\":\"" + fusionResult.decision().name() + "\",\"error\":\"SERIALIZATION_FAILED\"}";
        }

        UUID checkpointId = checkpointRepository.saveCheckpoint(
            entityId,
            statePayload,
            fusionResult.finalRisk(),
            classification
        );

        return Optional.of(checkpointId);
    }
}
