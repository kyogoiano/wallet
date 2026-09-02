package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.fraud.intelligence.propagation.PropagatedEntityRisk;
import br.com.wallet.fraud.intelligence.propagation.PropagationEvaluationDispatcher;
import br.com.wallet.fraud.intelligence.propagation.PropagationResult;
import br.com.wallet.fraud.intelligence.propagation.RiskPropagationEngine;
import br.com.wallet.infrastructure.rest.api.FraudRiskPropagationApi;
import br.com.wallet.infrastructure.rest.dto.DispatchPropagationResponse;
import br.com.wallet.infrastructure.rest.dto.FraudRiskPropagationResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
public class FraudRiskPropagationController implements FraudRiskPropagationApi {

    private final PropagationEvaluationDispatcher dispatcher;
    private final RiskPropagationEngine propagationEngine;

    public FraudRiskPropagationController(
        @NonNull final PropagationEvaluationDispatcher dispatcher,
        @NonNull final RiskPropagationEngine propagationEngine
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.propagationEngine = Objects.requireNonNull(propagationEngine, "propagationEngine cannot be null");
    }

    @Override
    public ResponseEntity<DispatchPropagationResponse> dispatchEvaluation(
        UUID entityId,
        Instant asOf,
        String modelVersion
    ) {
        Instant evaluationTimestamp = asOf != null ? asOf : Instant.now();
        String version = modelVersion != null ? modelVersion : "v1";

        Optional<UUID> jobId = dispatcher.dispatch(entityId, evaluationTimestamp, version);

        return jobId.map(uuid -> ResponseEntity.status(HttpStatus.ACCEPTED).body(new DispatchPropagationResponse(
                uuid,
                entityId,
                "PENDING",
                evaluationTimestamp,
                version,
                "Propagation job successfully enqueued"
        ))).orElseGet(() -> ResponseEntity.status(HttpStatus.OK).body(new DispatchPropagationResponse(
                null,
                entityId,
                "ACTIVE_EXISTS",
                evaluationTimestamp,
                version,
                "Active propagation job already exists for this entity"
        )));
    }

    @Override
    public ResponseEntity<FraudRiskPropagationResponse> evaluateEntity(
        UUID entityId,
        Instant asOf
    ) {
        Instant evaluationTimestamp = asOf != null ? asOf : Instant.now();
        PropagationResult result = propagationEngine.evaluateEntity(entityId, evaluationTimestamp);

        final var targetRisks = createTargetRisks(result);

        return ResponseEntity.ok(new FraudRiskPropagationResponse(
            result.rootSourceId(),
            result.evaluatedAt(),
            result.pathsEvaluated(),
            result.modelVersion(),
            targetRisks
        ));
    }

    private static @NonNull HashMap<UUID, FraudRiskPropagationResponse.TargetRiskDto> createTargetRisks(final PropagationResult result) {
        final var targetRisks = new HashMap<UUID, FraudRiskPropagationResponse.TargetRiskDto>();
        for (final Map.Entry<UUID, PropagatedEntityRisk> entry : result.propagatedRisks().entrySet()) {
            PropagatedEntityRisk risk = entry.getValue();
            targetRisks.put(entry.getKey(), new FraudRiskPropagationResponse.TargetRiskDto(
                risk.entityId(),
                risk.propagatedRisk(),
                risk.shortestHopCount(),
                risk.primaryRelationship().name(),
                risk.evaluatedAt()
            ));
        }
        return targetRisks;
    }
}
