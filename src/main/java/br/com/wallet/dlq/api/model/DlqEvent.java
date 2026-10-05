package br.com.wallet.dlq.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DlqEvent(
        @NonNull UUID id,
        @NonNull UUID operationId,
        @Nullable UUID userId,
        @NonNull String subject,
        @NonNull DlqStatus status,
        @Nullable String error,
        @NonNull String payload,
        @NonNull Integer retryCount,
        @Nullable Instant nextRetryAt,
        @NonNull Instant createdAt,
        @Nullable Instant processedAt,
        @NonNull DlqFailureType failureType,
        @NonNull String eventType,
        @NonNull String tenantId
) {
    public DlqEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(retryCount, "retryCount must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(failureType, "failureType must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
    }
}
