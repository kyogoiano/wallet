package br.com.wallet.fraud.fusion.api;

import br.com.wallet.fraud.fusion.internal.persistence.FusionJobRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Dispatches and coalesces entity evaluation jobs into the durable queue (REQ-FUSION-004, REQ-FUSION-009).
 */
@Component
public class FusionEvaluationDispatcher {

    private final FusionJobRepository repository;

    public FusionEvaluationDispatcher(@NonNull final FusionJobRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @NonNull
    public UUID dispatch(
        @NonNull final UUID entityId,
        @NonNull final String modelVersion,
        @NonNull final Instant asOf,
        @Nullable final String payload
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        return repository.enqueueOrCoalesceJob(entityId, modelVersion, asOf, payload);
    }
}
