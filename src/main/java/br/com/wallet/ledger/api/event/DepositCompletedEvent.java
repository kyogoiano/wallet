package br.com.wallet.ledger.api.event;

import br.com.wallet.core.context.OperationOrigin;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record DepositCompletedEvent(
        @NonNull UUID walletId,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId,
        @NonNull OperationOrigin origin
) implements DomainEvent {
    public DepositCompletedEvent {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
    }

    public DepositCompletedEvent(@NonNull UUID walletId, @NonNull BigDecimal amount, @NonNull UUID operationId) {
        this(walletId, amount, operationId, OperationOrigin.USER);
    }

    @Override
    public DomainEventType eventType() { return DomainEventType.DEPOSIT_COMPLETED; }

    @Override
    public UUID aggregateId() { return operationId; }

    @Override
    public String aggregateType() { return "WALLET_OPERATION"; }

    @Override
    public UUID partitionKey() { return walletId; }

    @Override
    @NonNull
    public String toString() {
        return "DepositCompletedEvent{" +
                "walletId=" + walletId +
                ", amount=" + amount +
                ", operationId=" + operationId +
                ", origin=" + origin +
                '}';
    }
}
