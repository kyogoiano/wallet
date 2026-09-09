package br.com.wallet.fraud.fusion.internal.orchestration;

import br.com.wallet.fraud.fusion.api.CheckpointRepository;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.internal.policy.RiskDecisionPolicy;
import br.com.wallet.fraud.investigation.api.InvestigationService;
import br.com.wallet.fraud.investigation.api.model.FraudInvestigationDossier;
import br.com.wallet.fraud.investigation.api.model.InvestigationGenerationStatus;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Dispatches investigations to Phase 0.7 InvestigationService under REVIEW and RESTRICT decisions (I-FUSION-007).
 * Serializes rich structured checkpoints and enforces graceful degradation on inference failures (I-VEC-009).
 */
@Component
public class InvestigationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InvestigationDispatcher.class);

    private final InvestigationService investigationService;
    private final CheckpointRepository checkpointRepository;
    private final RiskDecisionPolicy decisionPolicy;
    private final ObjectMapper objectMapper;

    public InvestigationDispatcher(
        @NonNull final InvestigationService investigationService,
        @NonNull final CheckpointRepository checkpointRepository,
        @NonNull final RiskDecisionPolicy decisionPolicy,
        @NonNull final ObjectMapper objectMapper
    ) {
        this.investigationService = Objects.requireNonNull(investigationService, "investigationService cannot be null");
        this.checkpointRepository = Objects.requireNonNull(checkpointRepository, "checkpointRepository cannot be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
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
