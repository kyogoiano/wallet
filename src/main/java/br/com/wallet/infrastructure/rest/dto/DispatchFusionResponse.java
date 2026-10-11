package br.com.wallet.infrastructure.rest.dto;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DispatchFusionResponse(
    @NonNull UUID jobId,
    @NonNull UUID entityId,
    @NonNull String status,
    @NonNull Instant asOf,
    @NonNull String modelVersion,
    @NonNull String message
) {
    public DispatchFusionResponse {
        Objects.requireNonNull(jobId, "jobId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }
}
