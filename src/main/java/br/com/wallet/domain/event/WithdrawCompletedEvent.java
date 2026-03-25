package br.com.wallet.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawCompletedEvent(
        @JsonProperty(value = "walletId", required = true) UUID walletId,
        @JsonProperty(value = "amount", required = true) BigDecimal amount,
        @JsonProperty(value = "operationId", required = true) UUID operationId
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
