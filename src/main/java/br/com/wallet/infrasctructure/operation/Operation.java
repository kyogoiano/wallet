package br.com.wallet.infrasctructure.operation;

import java.util.UUID;

public record Operation(UUID id, OperationStatus status) {
}
