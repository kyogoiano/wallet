package br.com.wallet.savings.internal.domain;

import br.com.wallet.savings.api.model.SavingsRuleType;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record IntendedSweepAction(
        @NonNull UUID planId,
        @NonNull UUID ruleId,
        @NonNull SavingsRuleType ruleType,
        @NonNull UUID sourceWalletId,
        @NonNull UUID targetWalletId,
        @NonNull BigDecimal sweepAmount
) {
    public IntendedSweepAction {
        Objects.requireNonNull(planId, "planId cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Objects.requireNonNull(ruleType, "ruleType cannot be null");
        Objects.requireNonNull(sourceWalletId, "sourceWalletId cannot be null");
        Objects.requireNonNull(targetWalletId, "targetWalletId cannot be null");
        Objects.requireNonNull(sweepAmount, "sweepAmount cannot be null");
    }
}
