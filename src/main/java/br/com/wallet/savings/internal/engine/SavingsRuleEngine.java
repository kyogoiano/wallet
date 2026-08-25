package br.com.wallet.savings.internal.engine;

import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.domain.IntendedSweepAction;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class SavingsRuleEngine {

    private final RoundUpCalculator roundUpCalculator;
    private final PercentageCalculator percentageCalculator;
    private final ThresholdCalculator thresholdCalculator;

    public SavingsRuleEngine(
            @NonNull final RoundUpCalculator roundUpCalculator,
            @NonNull final PercentageCalculator percentageCalculator,
            @NonNull final ThresholdCalculator thresholdCalculator
    ) {
        this.roundUpCalculator = Objects.requireNonNull(roundUpCalculator, "roundUpCalculator cannot be null");
        this.percentageCalculator = Objects.requireNonNull(percentageCalculator, "percentageCalculator cannot be null");
        this.thresholdCalculator = Objects.requireNonNull(thresholdCalculator, "thresholdCalculator cannot be null");
    }

    @NonNull
    public List<IntendedSweepAction> evaluateDeposit(
            @NonNull final SavingsPlan plan,
            @NonNull final BigDecimal depositAmount,
            @NonNull final BigDecimal currentBalance
    ) {
        Objects.requireNonNull(plan, "plan cannot be null");
        Objects.requireNonNull(depositAmount, "depositAmount cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");

        if (!plan.isActive()) {
            return List.of();
        }

        final List<IntendedSweepAction> actions = new ArrayList<>();
        BigDecimal runningBalance = currentBalance;
        final BigDecimal minRetained = plan.minimumRetainedBalance();

        // 1. Percentage Rule
        Optional<SavingsRule> percentageRuleOpt = plan.rules().stream()
                .filter(SavingsRule::isActive)
                .filter(r -> r.ruleType() == SavingsRuleType.PERCENTAGE)
                .findFirst();

        if (percentageRuleOpt.isPresent() && percentageRuleOpt.get().percentageRate() != null) {
            SavingsRule rule = percentageRuleOpt.get();
            BigDecimal calculated = percentageCalculator.calculate(depositAmount, rule.percentageRate());
            BigDecimal maxLiquidity = runningBalance.subtract(minRetained).max(BigDecimal.ZERO);
            BigDecimal actualSweep = calculated.min(maxLiquidity);

            if (actualSweep.compareTo(BigDecimal.ZERO) > 0) {
                actions.add(new IntendedSweepAction(
                        plan.id(), rule.id(), SavingsRuleType.PERCENTAGE,
                        plan.sourceWalletId(), plan.targetWalletId(), actualSweep
                ));
                runningBalance = runningBalance.subtract(actualSweep);
            }
        }

        // 2. Threshold Rule (evaluated on projected balance)
        Optional<SavingsRule> thresholdRuleOpt = plan.rules().stream()
                .filter(SavingsRule::isActive)
                .filter(r -> r.ruleType() == SavingsRuleType.THRESHOLD)
                .findFirst();

        if (thresholdRuleOpt.isPresent() && thresholdRuleOpt.get().ceilingThreshold() != null) {
            SavingsRule rule = thresholdRuleOpt.get();
            BigDecimal calculated = thresholdCalculator.calculate(runningBalance, rule.ceilingThreshold());
            BigDecimal remainingLiquidity = runningBalance.subtract(minRetained).max(BigDecimal.ZERO);
            BigDecimal actualSweep = calculated.min(remainingLiquidity);

            if (actualSweep.compareTo(BigDecimal.ZERO) > 0) {
                actions.add(new IntendedSweepAction(
                        plan.id(), rule.id(), SavingsRuleType.THRESHOLD,
                        plan.sourceWalletId(), plan.targetWalletId(), actualSweep
                ));
            }
        }

        return List.copyOf(actions);
    }

    @NonNull
    public List<IntendedSweepAction> evaluateTransfer(
            @NonNull final SavingsPlan plan,
            @NonNull final BigDecimal transferAmount,
            @NonNull final BigDecimal currentBalance
    ) {
        Objects.requireNonNull(plan, "plan cannot be null");
        Objects.requireNonNull(transferAmount, "transferAmount cannot be null");
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");

        if (!plan.isActive()) {
            return List.of();
        }

        Optional<SavingsRule> roundUpRuleOpt = plan.rules().stream()
                .filter(SavingsRule::isActive)
                .filter(r -> r.ruleType() == SavingsRuleType.ROUND_UP)
                .findFirst();

        if (roundUpRuleOpt.isEmpty() || roundUpRuleOpt.get().stepAmount() == null) {
            return List.of();
        }

        SavingsRule rule = roundUpRuleOpt.get();
        BigDecimal calculated = roundUpCalculator.calculate(transferAmount, rule.stepAmount());
        BigDecimal maxLiquidity = currentBalance.subtract(plan.minimumRetainedBalance()).max(BigDecimal.ZERO);
        BigDecimal actualSweep = calculated.min(maxLiquidity);

        if (actualSweep.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        return List.of(new IntendedSweepAction(
                plan.id(), rule.id(), SavingsRuleType.ROUND_UP,
                plan.sourceWalletId(), plan.targetWalletId(), actualSweep
        ));
    }
}
