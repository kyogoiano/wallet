package br.com.wallet.dlq.api.dto;

import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record DlqOperationResponse(
        @NonNull UUID id,
        @NonNull UUID operationId,
        @Nullable UUID userId,
        @NonNull String subject,
        @NonNull DlqStatus status,
        @Nullable String error,
        @NonNull String payload,
        int retryCount,
        @Nullable Instant nextRetryAt,
        @NonNull Instant createdAt,
        @Nullable Instant processedAt,
        @NonNull DlqFailureType failureType,
        @NonNull String eventType
) {
    public static DlqOperationResponse from(@NonNull final DlqEvent event) {
        return new DlqOperationResponse(
                event.id(),
                event.operationId(),
                event.userId(),
                event.subject(),
                event.status(),
                event.error(),
                event.payload(),
                event.retryCount(),
                event.nextRetryAt(),
                event.createdAt(),
                event.processedAt(),
                event.failureType(),
                event.eventType()
        );
    }
}
