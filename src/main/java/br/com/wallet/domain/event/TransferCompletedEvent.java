package br.com.wallet.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
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
        @JsonProperty(value = "from", required = true) UUID from,
        @JsonProperty(value = "to", required = true) UUID to,
        @JsonProperty(value = "amount", required = true) BigDecimal amount,
        @JsonProperty(value = "operationId", required = true) UUID operationId
) implements DomainEvent {

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
