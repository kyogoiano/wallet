package br.com.wallet.infrasctructure.outbox;

import java.util.UUID;

public record OutboxEvent(UUID id, String eventType, String payload) {
}
