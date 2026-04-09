package br.com.wallet.infrasctructure.messaging.dlq;

import java.time.Instant;
import java.util.UUID;

public record DlqEvent(UUID id,
                       UUID operationId,
                       String subject,
                       DlqStatus status,
                       String error,
                       String payload,
                       Integer retryCount,
                       Instant nextRetryAt,
                       Instant createdAt,
                       Instant processedAt,
                       DlqFailureType failureType) {
}