package br.com.wallet.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferCompletedEvent(
        UUID from,
        UUID to,
        BigDecimal amount,
        UUID operationId
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
     * Partition by source wallet to ensure debit ordering.
     * Credit side may arrive out of order (acceptable trade-off).
     * But dual events will increase complexity
     */
    @Override
    public UUID partitionKey() { return from; }
}
