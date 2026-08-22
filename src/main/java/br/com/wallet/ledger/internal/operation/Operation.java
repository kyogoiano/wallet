package br.com.wallet.ledger.internal.operation;

import java.util.UUID;

public record Operation(UUID id, OperationStatus status) {
}
