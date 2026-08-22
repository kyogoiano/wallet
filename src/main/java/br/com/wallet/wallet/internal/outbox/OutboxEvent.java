package br.com.wallet.wallet.internal.outbox;

import java.util.UUID;

public record OutboxEvent(UUID id, String eventType, String payload, Integer retryCount) {
}
