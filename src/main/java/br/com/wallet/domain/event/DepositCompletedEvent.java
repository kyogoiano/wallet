package br.com.wallet.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositCompletedEvent(
        @JsonProperty(value = "walletId", required = true) UUID walletId,
        @JsonProperty(value = "amount", required = true) BigDecimal amount,
        @JsonProperty(value = "operationId", required = true) UUID operationId
) implements DomainEvent {

    public String eventType() { return "DEPOSIT_COMPLETED"; }
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
