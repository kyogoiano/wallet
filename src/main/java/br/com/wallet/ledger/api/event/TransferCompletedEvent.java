package br.com.wallet.ledger.api.event;

import br.com.wallet.core.context.OperationOrigin;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Transfer funds completed event
 * @param from
 * @param to
 * @param amount
 * @param operationId
 * @param origin
 */
public record TransferCompletedEvent(
        @NonNull UUID from,
        @NonNull UUID to,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId,
        @NonNull OperationOrigin origin
) implements DomainEvent {
    public TransferCompletedEvent {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(origin, "origin cannot be null");
    }

    public TransferCompletedEvent(@NonNull UUID from, @NonNull UUID to, @NonNull BigDecimal amount, @NonNull UUID operationId) {
        this(from, to, amount, operationId, OperationOrigin.USER);
    }

    @Override
    public DomainEventType eventType() {
        return DomainEventType.TRANSFER_COMPLETED;
    }

    @Override
    public UUID aggregateId() {
        return operationId;
    }

    @Override
    public String aggregateType() {
        return "WALLET_OPERATION";
    }

    @Override
    public UUID partitionKey() { return from; }

    @NonNull
    @Override
    public String toString() {
        return "TransferCompletedEvent{" +
                "from=" + from +
                ", to=" + to +
                ", amount=" + amount +
                ", operationId=" + operationId +
                ", origin=" + origin +
                '}';
    }
}
