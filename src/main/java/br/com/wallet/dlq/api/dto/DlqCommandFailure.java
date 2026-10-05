package br.com.wallet.dlq.api.dto;

import br.com.wallet.dlq.api.model.DlqFailureType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates ingress command failure metadata for durable handoff into DLQ storage (I-TDLQ-009).
 */
public record DlqCommandFailure(
        @NonNull UUID operationId,
        @Nullable UUID userId,
        @NonNull String subject,
        @NonNull String payload,
        @NonNull DlqFailureType failureType,
        @Nullable String error,
        @NonNull String eventType,
        @NonNull String tenantId
) {
    public DlqCommandFailure {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(failureType, "failureType must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
    }
}
