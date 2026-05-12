package br.com.wallet.domain.event;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record WithdrawCompletedEvent(
        @NonNull UUID walletId,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId
) implements DomainEvent {
    public WithdrawCompletedEvent {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
    }

    @Override
    public DomainEventType eventType() { return DomainEventType.WITHDRAW_COMPLETED; }
    @Override
    public UUID aggregateId() { return operationId; }
    @Override
    public String aggregateType() { return "WALLET_OPERATION"; }
    @Override
    public UUID partitionKey() { return walletId; }

    @NonNull
    @Override
    public String toString() {
        return "WithdrawCompletedEvent{" +
                "walletId=" + walletId +
                ", amount=" + amount +
                ", operationId=" + operationId +
                '}';
    }
}
