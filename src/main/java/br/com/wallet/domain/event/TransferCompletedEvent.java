package br.com.wallet.domain.event;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Transfer funds completed event
 * Jackson is useful here to validate event contracts
 * @param from
 * @param to
 * @param amount
 * @param operationId
 */
public record TransferCompletedEvent(
        @NonNull UUID from,
        @NonNull UUID to,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId
) implements DomainEvent {
    public TransferCompletedEvent {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
    }

    @Override
    public String eventType() {
        return "TRANSFER_COMPLETED";
    }

    @Override
    public UUID aggregateId() {
        return operationId;
    }

    @Override
    public String aggregateType() {
        return "WALLET_OPERATION";
    }

    /**
     * Partition by source wallet (from) to ensure debit ordering.
     * Credit side may arrive out of order (acceptable trade-off).
     * But dual events will increase complexity
     */
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
                '}';
    }
}
