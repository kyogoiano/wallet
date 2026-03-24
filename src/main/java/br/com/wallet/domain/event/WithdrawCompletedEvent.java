package br.com.wallet.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawCompletedEvent(
        UUID walletId,
        BigDecimal amount,
        UUID operationId
) implements DomainEvent {

    @Override
    public String eventType() { return "WITHDRAW_COMPLETED"; }
    @Override
    public UUID aggregateId() { return operationId; }
    @Override
    public String aggregateType() { return "WALLET_OPERATION"; }
    @Override
    public UUID partitionKey() { return walletId; }
}
