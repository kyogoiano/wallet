package br.com.wallet.ledger.api.event;

import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.RuleType;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record FraudEvent (@NonNull UUID from,
                          UUID to,
                          @NonNull BigDecimal amount,
                          @NonNull UUID operationId,
                          @NonNull Instant timestamp,
                          @NonNull FraudDecision decision,
                          int riskScore,
                          List<RuleType> triggeredRules,
                          @NonNull String tenantId) implements DomainEvent {

    public FraudEvent {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(decision, "decision cannot be null");
        if (tenantId.isBlank()) {
            throw new br.com.wallet.core.exceptions.TenantContextMissingException("Tenant identifier is required for FraudEvent");
        }
    }

    public FraudEvent(@NonNull UUID from,
                      UUID to,
                      @NonNull BigDecimal amount,
                      @NonNull UUID operationId,
                      @NonNull Instant timestamp,
                      @NonNull FraudDecision decision,
                      int riskScore,
                      List<RuleType> triggeredRules) {
        this(from, to, amount, operationId, timestamp, decision, riskScore, triggeredRules, "tenant-alpha");
    }
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
