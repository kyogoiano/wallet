package br.com.wallet.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCompletedEvent(
        UUID walletId,
        BigDecimal amount,
        UUID operationId
) implements DomainEvent {

    public String eventType() { return "DEPOSIT_COMPLETED"; }
    public UUID aggregateId() { return operationId; }
    public String aggregateType() { return "WALLET_OPERATION"; }
    public UUID partitionKey() { return walletId; }
}
