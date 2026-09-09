package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.fraud.fusion.api.CheckpointRepository;
import br.com.wallet.fraud.fusion.api.FraudSignalFusionService;
import br.com.wallet.fraud.fusion.api.FusionEvaluationDispatcher;
import br.com.wallet.fraud.fusion.api.model.AnalystReviewRequest;
import br.com.wallet.fraud.fusion.api.model.AnalystReviewResponse;
import br.com.wallet.fraud.fusion.api.model.CheckpointRecord;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.infrastructure.rest.api.FraudSignalFusionApi;
import br.com.wallet.infrastructure.rest.dto.DispatchFusionResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller implementing asynchronous dispatch, synchronous evaluation, and analyst reviews for Fraud Signal Fusion.
 */
@RestController
public class FraudSignalFusionController implements FraudSignalFusionApi {

    private static final Logger log = LoggerFactory.getLogger(FraudSignalFusionController.class);

    private final FusionEvaluationDispatcher dispatcher;
    private final FraudSignalFusionService fusionService;
    private final CheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;

    public FraudSignalFusionController(
        @NonNull final FusionEvaluationDispatcher dispatcher,
        @NonNull final FraudSignalFusionService fusionService,
        @NonNull final CheckpointRepository checkpointRepository,
        @NonNull final ObjectMapper objectMapper
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.fusionService = Objects.requireNonNull(fusionService, "fusionService cannot be null");
        this.checkpointRepository = Objects.requireNonNull(checkpointRepository, "checkpointRepository cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public ResponseEntity<DispatchFusionResponse> dispatchEvaluation(
        final UUID entityId,
        final Instant asOf,
        final String modelVersion,
        final Map<String, Object> payload
    ) {
        Instant evaluationTimestamp = asOf != null ? asOf : Instant.now();
        String resolvedModelVersion = (payload != null && payload.get("modelVersion") != null)
            ? payload.get("modelVersion").toString()
            : (modelVersion != null ? modelVersion : "v1");

        String payloadJson = null;
        if (payload != null && !payload.isEmpty()) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JacksonException e) {
                log.warn("Failed to serialize dispatch payload to JSON, falling back to toString: {}", e.getMessage());
                payloadJson = payload.toString();
            }
        }

        UUID jobId = dispatcher.dispatch(entityId, resolvedModelVersion, evaluationTimestamp, payloadJson);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DispatchFusionResponse(
            jobId,
            entityId,
            "PENDING",
            evaluationTimestamp,
            resolvedModelVersion,
            "Fusion evaluation job successfully enqueued"
        ));
    }

    @Override
    public ResponseEntity<RiskFusionResult> evaluateEntity(final UUID entityId) {
        RiskFusionResult result = fusionService.evaluateEntity(entityId);
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<AnalystReviewResponse> submitReview(
        final UUID checkpointId,
        final AnalystReviewRequest request
    ) {
        CheckpointRecord checkpoint = checkpointRepository.findCheckpointById(checkpointId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Checkpoint not found: " + checkpointId));

        UUID reviewId = checkpointRepository.saveReview(
            checkpointId,
            checkpoint.entityId(),
            request.analystId(),
            request.verdict(),
            request.notes()
        );

        return ResponseEntity.ok(new AnalystReviewResponse(
            reviewId,
            checkpointId,
            checkpoint.entityId(),
            "ANALYST_REVIEWED",
            request.verdict()
        ));
    }
}
