package br.com.wallet.savings.internal.domain;

import br.com.wallet.savings.api.model.SavingsRuleType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record SavingsRule(
        @NonNull UUID id,
        @NonNull UUID planId,
        @NonNull SavingsRuleType ruleType,
        @Nullable BigDecimal stepAmount,
        @Nullable BigDecimal percentageRate,
        @Nullable BigDecimal ceilingThreshold,
        boolean isActive
) {
    public SavingsRule {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(planId, "planId cannot be null");
        Objects.requireNonNull(ruleType, "ruleType cannot be null");
    }

    public static SavingsRule roundUp(UUID id, UUID planId, BigDecimal stepAmount) {
        return new SavingsRule(id, planId, SavingsRuleType.ROUND_UP, stepAmount, null, null, true);
    }

    public static SavingsRule percentage(UUID id, UUID planId, BigDecimal percentageRate) {
        return new SavingsRule(id, planId, SavingsRuleType.PERCENTAGE, null, percentageRate, null, true);
    }

    public static SavingsRule threshold(UUID id, UUID planId, BigDecimal ceilingThreshold) {
        return new SavingsRule(id, planId, SavingsRuleType.THRESHOLD, null, null, ceilingThreshold, true);
    }
}
