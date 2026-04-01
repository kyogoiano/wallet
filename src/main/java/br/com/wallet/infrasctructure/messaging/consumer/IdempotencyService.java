package br.com.wallet.infrasctructure.messaging.consumer;

import java.util.UUID;

public interface IdempotencyService {
    boolean isProcessed(UUID operationId);
    boolean markProcessed(UUID operationId);
}