package br.com.wallet.unit.savings.engine;

import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.domain.IntendedSweepAction;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.engine.PercentageCalculator;
import br.com.wallet.savings.internal.engine.RoundUpCalculator;
import br.com.wallet.savings.internal.engine.SavingsRuleEngine;
import br.com.wallet.savings.internal.engine.ThresholdCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SavingsRuleEngine Unit Tests")
class SavingsRuleEngineTest {

    private SavingsRuleEngine engine;

    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        engine = new SavingsRuleEngine(
                new RoundUpCalculator(),
                new PercentageCalculator(),
                new ThresholdCalculator()
        );
    }

    @Test
    @DisplayName("Should evaluate deposit with Percentage rule only")
    void shouldEvaluateDepositWithPercentageOnly() {
        SavingsRule percentageRule = SavingsRule.percentage(UUID.randomUUID(), planId, new BigDecimal("10.00"));
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(percentageRule), Instant.now(), Instant.now()
        );

        List<IntendedSweepAction> actions = engine.evaluateDeposit(
                plan, new BigDecimal("5000.00"), new BigDecimal("5000.00")
        );

        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst().ruleType()).isEqualTo(SavingsRuleType.PERCENTAGE);
        assertThat(actions.getFirst().sweepAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Should evaluate deposit with Percentage and Threshold in deterministic order with projected balance")
    void shouldEvaluateDepositWithPercentageAndThresholdOrdered() {
        SavingsRule percentageRule = SavingsRule.percentage(UUID.randomUUID(), planId, new BigDecimal("10.00"));
        SavingsRule thresholdRule = SavingsRule.threshold(UUID.randomUUID(), planId, new BigDecimal("10000.00"));
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(thresholdRule, percentageRule), // Deliberately reversed in list to test ordering
                Instant.now(), Instant.now()
        );

        // Current balance 15,000 (after 5,000 deposit)
        // 1. Percentage 10% of 5,000 = 500.00
        // 2. Projected balance = 15,000 - 500 = 14,500.00
        // 3. Threshold ceiling 10,000 -> excess = 4,500.00
        List<IntendedSweepAction> actions = engine.evaluateDeposit(
                plan, new BigDecimal("5000.00"), new BigDecimal("15000.00")
        );

        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).ruleType()).isEqualTo(SavingsRuleType.PERCENTAGE);
        assertThat(actions.get(0).sweepAmount()).isEqualByComparingTo("500.00");
        assertThat(actions.get(1).ruleType()).isEqualTo(SavingsRuleType.THRESHOLD);
        assertThat(actions.get(1).sweepAmount()).isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("Should clamp sweep amounts respecting minimumRetainedBalance (Liquidity Intent Protection)")
    void shouldClampSweepRespectingMinimumRetainedBalance() {
        SavingsRule percentageRule = SavingsRule.percentage(UUID.randomUUID(), planId, new BigDecimal("50.00"));
        SavingsRule thresholdRule = SavingsRule.threshold(UUID.randomUUID(), planId, new BigDecimal("500.00"));
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet,
                new BigDecimal("800.00"), // Minimum retained balance = 800
                "ACTIVE",
                List.of(percentageRule, thresholdRule),
                Instant.now(), Instant.now()
        );

        // Current balance = 1,000.00, Deposit = 1,000.00.
        // Max liquidity = 1,000 - 800 = 200.00.
        // Percentage 50% of 1,000 = 500.00 -> clamped to 200.00!
        // Projected balance = 1,000 - 200 = 800.00.
        // Threshold ceiling 500 on 800 -> excess 300, but remaining liquidity = 800 - 800 = 0 -> clamped to 0 (skipped)!
        List<IntendedSweepAction> actions = engine.evaluateDeposit(
                plan, new BigDecimal("1000.00"), new BigDecimal("1000.00")
        );

        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst().ruleType()).isEqualTo(SavingsRuleType.PERCENTAGE);
        assertThat(actions.getFirst().sweepAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Should evaluate transfer with Round-Up micro-savings")
    void shouldEvaluateTransferWithRoundUp() {
        SavingsRule roundUpRule = SavingsRule.roundUp(UUID.randomUUID(), planId, new BigDecimal("5.00"));
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(roundUpRule), Instant.now(), Instant.now()
        );

        List<IntendedSweepAction> actions = engine.evaluateTransfer(
                plan, new BigDecimal("47.30"), new BigDecimal("100.00")
        );

        assertThat(actions).hasSize(1);
        assertThat(actions.getFirst().ruleType()).isEqualTo(SavingsRuleType.ROUND_UP);
        assertThat(actions.getFirst().sweepAmount()).isEqualByComparingTo("2.70");
    }

    @Test
    @DisplayName("Should produce no actions when Round-Up delta is zero (exact multiple)")
    void shouldProduceNoActionsWhenRoundUpDeltaIsZero() {
        SavingsRule roundUpRule = SavingsRule.roundUp(UUID.randomUUID(), planId, new BigDecimal("5.00"));
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(roundUpRule), Instant.now(), Instant.now()
        );

        List<IntendedSweepAction> actions = engine.evaluateTransfer(
                plan, new BigDecimal("50.00"), new BigDecimal("100.00")
        );

        assertThat(actions).isEmpty();
    }

    @Test
    @DisplayName("Should produce no actions when plan is paused or inactive")
    void shouldProduceNoActionsWhenPlanIsPaused() {
        SavingsRule roundUpRule = SavingsRule.roundUp(UUID.randomUUID(), planId, new BigDecimal("5.00"));
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "PAUSED",
                List.of(roundUpRule), Instant.now(), Instant.now()
        );

        List<IntendedSweepAction> actions = engine.evaluateTransfer(
                plan, new BigDecimal("47.30"), new BigDecimal("100.00")
        );

        assertThat(actions).isEmpty();
    }
}
