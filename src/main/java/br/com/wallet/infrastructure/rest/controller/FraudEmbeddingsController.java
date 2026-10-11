package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.fraud.embeddings.api.BehavioralEmbeddingEngine;
import br.com.wallet.fraud.embeddings.api.EmbeddingEvaluationDispatcher;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import br.com.wallet.infrastructure.rest.api.FraudEmbeddingsApi;
import br.com.wallet.infrastructure.rest.dto.DispatchEmbeddingResponse;
import br.com.wallet.infrastructure.rest.dto.ExtractEmbeddingsResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@RestController
public class FraudEmbeddingsController implements FraudEmbeddingsApi {

    private final BehavioralEmbeddingEngine embeddingEngine;
    private final BehavioralFeatureStore featureStore;
    private final EmbeddingEvaluationDispatcher dispatcher;

    public FraudEmbeddingsController(
        @NonNull final BehavioralEmbeddingEngine embeddingEngine,
        @NonNull final BehavioralFeatureStore featureStore,
        @NonNull final EmbeddingEvaluationDispatcher dispatcher
    ) {
        this.embeddingEngine = Objects.requireNonNull(embeddingEngine, "embeddingEngine cannot be null");
        this.featureStore = Objects.requireNonNull(featureStore, "featureStore cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
    }

    @Override
    public ResponseEntity<ExtractEmbeddingsResponse> extractAndEvaluate(final UUID entityId) {
        ArchetypeMatch match = embeddingEngine.evaluateAndPersistRisk(entityId);
        BehavioralFeatureVector vector = featureStore.findFeatures(entityId)
            .orElseGet(() -> BehavioralFeatureVector.inactive(entityId));

        return ResponseEntity.ok(new ExtractEmbeddingsResponse(
            entityId,
            match.weightedSimilarity(),
            match.archetypeId(),
            match.directionalSimilarity(),
            vector.featureMagnitude(),
            vector.transactionCount(),
            vector.transactionVolume()
        ));
    }

    @Override
    public ResponseEntity<DispatchEmbeddingResponse> dispatchEvaluation(final UUID entityId, final Instant asOf) {
        Instant evaluationTimestamp = asOf != null ? asOf : Instant.now();
        boolean enqueued = dispatcher.dispatchEvaluation(entityId, evaluationTimestamp);

        if (enqueued) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DispatchEmbeddingResponse(
                entityId, "PENDING", evaluationTimestamp, "Embedding evaluation job successfully enqueued"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(new DispatchEmbeddingResponse(
                entityId, "ACTIVE_EXISTS", evaluationTimestamp, "Active embedding evaluation job already in progress"
            ));
        }
    }
}
