package br.com.wallet.fraud.intelligence.propagation;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Public capability interface to dispatch asynchronous risk propagation evaluations.
 */
public interface PropagationEvaluationDispatcher {

    /**
     * Enqueues an asynchronous risk propagation evaluation for the target entity as of now.
     *
     * @param entityId target entity UUID
     * @param modelVersion model version (e.g. "v1")
     * @return job UUID if enqueued, or empty if an active job already exists
     */
    @NonNull
    Optional<UUID> dispatch(@NonNull UUID entityId, @NonNull String modelVersion);

    /**
     * Enqueues an asynchronous risk propagation evaluation with an explicit historical asOf timestamp.
     *
     * @param entityId target entity UUID
     * @param asOf historical point in time
     * @param modelVersion model version
     * @return job UUID if enqueued, or empty if an active job already exists
     */
    @NonNull
    Optional<UUID> dispatch(@NonNull UUID entityId, @NonNull Instant asOf, @NonNull String modelVersion);
}
