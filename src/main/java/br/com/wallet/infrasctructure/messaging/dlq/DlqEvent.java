package br.com.wallet.infrasctructure.messaging.dlq;

import br.com.wallet.core.tracing.TraceContext;

import java.time.Instant;
import java.util.Map;
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
                       DlqFailureType failureType) implements TraceContext {
    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "dlq.id", id.toString(),
                "dlq.subject", subject,
                "dlq.status", status.name(),
                "dlq.error",  error,
                "dlq.retryCount", retryCount.toString(),
                "dlq.failureType", failureType.name()
        );
    }


}