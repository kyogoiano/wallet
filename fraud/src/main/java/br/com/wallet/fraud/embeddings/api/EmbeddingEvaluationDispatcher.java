package br.com.wallet.fraud.embeddings.api;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Public API for asynchronously enqueuing entities for behavioral feature extraction and embedding updates.
 */
public interface EmbeddingEvaluationDispatcher {

    boolean dispatchEvaluation(@NonNull UUID entityId, @NonNull Instant asOf);

    boolean dispatchEvaluation(@NonNull UUID entityId);
}
