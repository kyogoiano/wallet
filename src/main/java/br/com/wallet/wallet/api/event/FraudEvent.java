package br.com.wallet.wallet.api.event;

import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.RuleType;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FraudEvent (@NonNull UUID from,
                          UUID to,
                          @NonNull BigDecimal amount,
                          @NonNull UUID operationId,
                          @NonNull Instant timestamp,
                          @NonNull FraudDecision decision,
                          int riskScore,
                          List<RuleType> triggeredRules) implements DomainEvent {
    @Override
    public DomainEventType eventType() {
        return DomainEventType.FRAUD;
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
    public UUID partitionKey() {
        return from;
    }

}
