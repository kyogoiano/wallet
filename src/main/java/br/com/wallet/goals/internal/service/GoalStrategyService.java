package br.com.wallet.goals.internal.service;

import br.com.wallet.goals.api.GoalStrategyUseCase;
import br.com.wallet.goals.api.dto.GoalStrategyResponse;
import br.com.wallet.goals.api.dto.SimulateGoalCommand;
import br.com.wallet.goals.api.model.*;
import br.com.wallet.goals.internal.engine.GoalStrategyEngine;
import br.com.wallet.goals.internal.persistence.CashflowProfileDao;
import br.com.wallet.goals.internal.persistence.FinancialGoalDao;
import br.com.wallet.ledger.api.BalanceUseCase;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GoalStrategyService implements GoalStrategyUseCase {

    private static final Logger log = LoggerFactory.getLogger(GoalStrategyService.class);

    private final FinancialGoalDao goalDao;
    private final CashflowProfileDao cashflowProfileDao;
    private final BalanceUseCase balanceUseCase;
    private final GoalStrategyEngine strategyEngine;

    public GoalStrategyService(
            @NonNull final FinancialGoalDao goalDao,
            @NonNull final CashflowProfileDao cashflowProfileDao,
            @NonNull final BalanceUseCase balanceUseCase,
            @NonNull final GoalStrategyEngine strategyEngine
    ) {
        this.goalDao = Objects.requireNonNull(goalDao, "goalDao cannot be null");
        this.cashflowProfileDao = Objects.requireNonNull(cashflowProfileDao, "cashflowProfileDao cannot be null");
        this.balanceUseCase = Objects.requireNonNull(balanceUseCase, "balanceUseCase cannot be null");
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine cannot be null");
    }

    @Override
    @NonNull
    public GoalStrategyResponse calculateStrategy(@NonNull final UUID goalId, @NonNull final LocalDate evaluationDate) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");

        final FinancialGoal goal = goalDao.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found with ID: " + goalId));

        final CashflowProfile profile = cashflowProfileDao.findByWalletId(goal.walletId()).orElse(null);

        final UUID balanceWalletId = goal.targetWalletId() != null ? goal.targetWalletId() : goal.walletId();
        final BigDecimal currentBalance = balanceUseCase.getBalance(balanceWalletId);

        final GoalStrategy strategy = strategyEngine.calculate(goal, currentBalance, profile, evaluationDate);
        return mapToResponse(strategy);
    }

    @Override
    @NonNull
    public GoalStrategyResponse simulate(@NonNull final SimulateGoalCommand command, @NonNull final LocalDate evaluationDate) {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");

        final FinancialGoal simulatedGoal = new FinancialGoal(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Simulated Goal",
                command.targetAmount(),
                command.targetDate(),
                GoalPriority.MEDIUM,
                GoalStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        final CashflowProfile simulatedProfile = new CashflowProfile(
                UUID.randomUUID(),
                simulatedGoal.userId(),
                simulatedGoal.walletId(),
                command.monthlyIncome(),
                command.monthlyCommittedExpenses(),
                command.minimumSafetyBuffer(),
                Instant.now()
        );

        final GoalStrategy strategy = strategyEngine.calculate(
                simulatedGoal,
                command.currentBalance(),
                simulatedProfile,
                evaluationDate
        );

        return mapToResponse(strategy);
    }

    @Override
    @NonNull
    public MultiGoalStrategyReport evaluateWallet(@NonNull final UUID walletId, @NonNull final LocalDate evaluationDate) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(evaluationDate, "evaluationDate cannot be null");

        final List<FinancialGoal> goals = goalDao.findByWalletId(walletId);
        final CashflowProfile profile = cashflowProfileDao.findByWalletId(walletId).orElse(null);
        final BigDecimal currentBalance = balanceUseCase.getBalance(walletId);

        return strategyEngine.evaluateWaterfall(walletId, goals, profile, currentBalance, evaluationDate);
    }

    private GoalStrategyResponse mapToResponse(final GoalStrategy strategy) {
        return new GoalStrategyResponse(
                strategy.goalId(),
                strategy.targetAmount(),
                strategy.currentAccumulatedAmount(),
                strategy.remainingDeficit(),
                strategy.remainingMonths(),
                strategy.requiredMonthlyContribution(),
                strategy.safeMonthlyContributionCapacity(),
                strategy.recommendedMonthlyContribution(),
                strategy.feasibility(),
                strategy.projectedCompletionDate(),
                strategy.evaluationDate()
        );
    }
}
