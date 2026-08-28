package br.com.wallet.dlq.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
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
        @NonNull String eventType
) {
}
