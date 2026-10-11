package br.com.wallet.unit.goals.engine;

import br.com.wallet.goals.api.model.*;
import br.com.wallet.goals.internal.engine.CashflowCapacityCalculator;
import br.com.wallet.goals.internal.engine.ContributionCalculator;
import br.com.wallet.goals.internal.engine.GoalStrategyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit Tests: GoalStrategyEngine")
class GoalStrategyEngineTest {

    private GoalStrategyEngine engine;

    @BeforeEach
    void setUp() {
        ContributionCalculator contributionCalculator = new ContributionCalculator();
        CashflowCapacityCalculator capacityCalculator = new CashflowCapacityCalculator();
        engine = new GoalStrategyEngine(contributionCalculator, capacityCalculator);
    }

    @Test
    @DisplayName("Should evaluate goal as ACHIEVED when current balance meets target")
    void shouldEvaluateAchievedGoal() {
        UUID walletId = UUID.randomUUID();
        FinancialGoal goal = new FinancialGoal(
                UUID.randomUUID(), UUID.randomUUID(), walletId, null,
                "Emergency Fund", new BigDecimal("10000.00"),
                LocalDate.of(2027, 12, 31), GoalPriority.HIGH, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("5000.00"), new BigDecimal("3000.00"), new BigDecimal("1000.00"),
                Instant.now()
        );
        BigDecimal currentBalance = new BigDecimal("10500.00");
        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);

        GoalStrategy strategy = engine.calculate(goal, currentBalance, profile, evaluationDate);

        assertThat(strategy.feasibility()).isEqualTo(GoalFeasibility.ACHIEVED);
        assertThat(strategy.remainingDeficit()).isEqualByComparingTo("0.00");
        assertThat(strategy.requiredMonthlyContribution()).isEqualByComparingTo("0.00");
        assertThat(strategy.recommendedMonthlyContribution()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Should evaluate goal as ON_TRACK when safe capacity covers required contribution")
    void shouldEvaluateOnTrackGoal() {
        UUID walletId = UUID.randomUUID();
        // Target: 20,000; Current: 5,000; Deficit: 15,000; Months: 15; Required: 1,000/mo
        FinancialGoal goal = new FinancialGoal(
                UUID.randomUUID(), UUID.randomUUID(), walletId, null,
                "Car Downpayment", new BigDecimal("20000.00"),
                LocalDate.of(2027, 11, 27), GoalPriority.MEDIUM, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        // Income: 8,000; Exp: 4,000; Buffer: 1,500 -> Safe Capacity: 2,500/mo
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("8000.00"), new BigDecimal("4000.00"), new BigDecimal("1500.00"),
                Instant.now()
        );
        BigDecimal currentBalance = new BigDecimal("5000.00");
        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);

        GoalStrategy strategy = engine.calculate(goal, currentBalance, profile, evaluationDate);

        assertThat(strategy.feasibility()).isEqualTo(GoalFeasibility.ON_TRACK);
        assertThat(strategy.remainingDeficit()).isEqualByComparingTo("15000.00");
        assertThat(strategy.requiredMonthlyContribution()).isEqualByComparingTo("1000.00");
        assertThat(strategy.safeMonthlyContributionCapacity()).isEqualByComparingTo("2500.00");
        assertThat(strategy.recommendedMonthlyContribution()).isEqualByComparingTo("1000.00");
        assertThat(strategy.projectedCompletionDate()).isNotNull();
    }

    @Test
    @DisplayName("Should evaluate goal as AT_RISK when safe capacity is positive but less than required contribution")
    void shouldEvaluateAtRiskGoal() {
        UUID walletId = UUID.randomUUID();
        // Target: 50,000; Current: 10,000; Deficit: 40,000; Months: 10; Required: 4,000/mo
        FinancialGoal goal = new FinancialGoal(
                UUID.randomUUID(), UUID.randomUUID(), walletId, null,
                "House Renovation", new BigDecimal("50000.00"),
                LocalDate.of(2027, 6, 27), GoalPriority.HIGH, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        // Income: 7,000; Exp: 4,000; Buffer: 1,000 -> Safe Capacity: 2,000/mo (< 4,000)
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("7000.00"), new BigDecimal("4000.00"), new BigDecimal("1000.00"),
                Instant.now()
        );
        BigDecimal currentBalance = new BigDecimal("10000.00");
        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);

        GoalStrategy strategy = engine.calculate(goal, currentBalance, profile, evaluationDate);

        assertThat(strategy.feasibility()).isEqualTo(GoalFeasibility.AT_RISK);
        assertThat(strategy.requiredMonthlyContribution()).isEqualByComparingTo("4000.00");
        assertThat(strategy.recommendedMonthlyContribution()).isEqualByComparingTo("2000.00");
        // Projected: 40,000 / 2,000 = 20 months -> 2026-08 + 20 mo = 2028-04
        assertThat(strategy.projectedCompletionDate()).isEqualTo(LocalDate.of(2028, 4, 27));
    }

    @Test
    @DisplayName("Should evaluate goal as UNACHIEVABLE when safe capacity is zero")
    void shouldEvaluateUnachievableGoal() {
        UUID walletId = UUID.randomUUID();
        FinancialGoal goal = new FinancialGoal(
                UUID.randomUUID(), UUID.randomUUID(), walletId, null,
                "Trip to Tokyo", new BigDecimal("15000.00"),
                LocalDate.of(2027, 8, 27), GoalPriority.LOW, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        // Income: 3,000; Exp: 3,000; Buffer: 500 -> Safe Capacity: 0.00
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("3000.00"), new BigDecimal("3000.00"), new BigDecimal("500.00"),
                Instant.now()
        );
        BigDecimal currentBalance = new BigDecimal("1000.00");
        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);

        GoalStrategy strategy = engine.calculate(goal, currentBalance, profile, evaluationDate);

        assertThat(strategy.feasibility()).isEqualTo(GoalFeasibility.UNACHIEVABLE);
        assertThat(strategy.safeMonthlyContributionCapacity()).isEqualByComparingTo("0.00");
        assertThat(strategy.recommendedMonthlyContribution()).isEqualByComparingTo("0.00");
        assertThat(strategy.projectedCompletionDate()).isNull();
    }

    @Test
    @DisplayName("Should evaluate multi-goal waterfall priority allocating capacity to higher priority first (I-GOAL-006)")
    void shouldEvaluateMultiGoalWaterfallPriority() {
        UUID walletId = UUID.randomUUID();
        // Safe capacity = 10,000 - 6,000 - 1,000 = 3,000.00 / month
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("10000.00"), new BigDecimal("6000.00"), new BigDecimal("1000.00"),
                Instant.now()
        );

        // Goal 1 (CRITICAL): Emergency Fund, Deficit 20,000, 10 months -> Required 2,000/mo
        FinancialGoal criticalGoal = new FinancialGoal(
                UUID.randomUUID(), UUID.randomUUID(), walletId, null,
                "Emergency Fund", new BigDecimal("20000.00"),
                LocalDate.of(2027, 6, 27), GoalPriority.CRITICAL, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        // Goal 2 (LOW): Vacation, Deficit 10,000, 5 months -> Required 2,000/mo
        FinancialGoal lowGoal = new FinancialGoal(
                UUID.randomUUID(), UUID.randomUUID(), walletId, null,
                "Vacation", new BigDecimal("10000.00"),
                LocalDate.of(2027, 1, 27), GoalPriority.LOW, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);

        // Pass goals in arbitrary order
        MultiGoalStrategyReport report = engine.evaluateWaterfall(
                walletId,
                List.of(lowGoal, criticalGoal),
                profile,
                BigDecimal.ZERO, // zero starting balance for simplicity
                evaluationDate
        );

        assertThat(report.totalSafeCapacity()).isEqualByComparingTo("3000.00");
        assertThat(report.goalStrategies()).hasSize(2);

        // Critical goal should get full 2,000 requirement -> ON_TRACK
        GoalStrategy criticalStrategy = report.goalStrategies().stream()
                .filter(s -> s.goalId().equals(criticalGoal.id()))
                .findFirst().orElseThrow();
        assertThat(criticalStrategy.recommendedMonthlyContribution()).isEqualByComparingTo("2000.00");
        assertThat(criticalStrategy.feasibility()).isEqualTo(GoalFeasibility.ON_TRACK);

        // Low goal gets remaining 1,000 of 3,000 capacity (requires 2,000) -> AT_RISK
        GoalStrategy lowStrategy = report.goalStrategies().stream()
                .filter(s -> s.goalId().equals(lowGoal.id()))
                .findFirst().orElseThrow();
        assertThat(lowStrategy.recommendedMonthlyContribution()).isEqualByComparingTo("1000.00");
        assertThat(lowStrategy.feasibility()).isEqualTo(GoalFeasibility.AT_RISK);
    }
}
