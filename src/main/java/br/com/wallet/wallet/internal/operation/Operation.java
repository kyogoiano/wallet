package br.com.wallet.wallet.internal.operation;

import java.util.UUID;

public record Operation(UUID id, OperationStatus status) {
}
