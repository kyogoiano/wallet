package br.com.wallet.wallet.api.event;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record DepositCompletedEvent(
        @NonNull UUID walletId,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId
) implements DomainEvent {
    public DepositCompletedEvent {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
    }

    public DomainEventType eventType() { return DomainEventType.DEPOSIT_COMPLETED; }
    public UUID aggregateId() { return operationId; }
    public String aggregateType() { return "WALLET_OPERATION"; }
    public UUID partitionKey() { return walletId; }

    @Override
    @NonNull
    public String toString() {
        return "DepositCompletedEvent{" +
                "walletId=" + walletId +
                ", amount=" + amount +
                ", operationId=" + operationId +
                '}';
    }
}
