package br.com.wallet.unit.goals.service;

import br.com.wallet.goals.api.dto.GoalStrategyResponse;
import br.com.wallet.goals.api.dto.SimulateGoalCommand;
import br.com.wallet.goals.api.model.*;
import br.com.wallet.goals.internal.engine.CashflowCapacityCalculator;
import br.com.wallet.goals.internal.engine.ContributionCalculator;
import br.com.wallet.goals.internal.engine.GoalStrategyEngine;
import br.com.wallet.goals.internal.persistence.CashflowProfileDao;
import br.com.wallet.goals.internal.persistence.FinancialGoalDao;
import br.com.wallet.goals.internal.service.GoalStrategyService;
import br.com.wallet.ledger.api.BalanceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Unit Tests: GoalStrategyService")
class GoalStrategyServiceTest {

    private FinancialGoalDao goalDao;
    private CashflowProfileDao profileDao;
    private BalanceUseCase balanceUseCase;
    private GoalStrategyEngine engine;
    private GoalStrategyService service;

    @BeforeEach
    void setUp() {
        goalDao = mock(FinancialGoalDao.class);
        profileDao = mock(CashflowProfileDao.class);
        balanceUseCase = mock(BalanceUseCase.class);
        engine = new GoalStrategyEngine(new ContributionCalculator(), new CashflowCapacityCalculator());
        service = new GoalStrategyService(goalDao, profileDao, balanceUseCase, engine);
    }

    @Test
    @DisplayName("Should calculate strategy for existing goal querying live balance")
    void shouldCalculateStrategyForGoal() {
        UUID goalId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        FinancialGoal goal = new FinancialGoal(
                goalId, UUID.randomUUID(), walletId, null,
                "House", new BigDecimal("100000.00"),
                LocalDate.of(2028, 12, 31), GoalPriority.HIGH, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        CashflowProfile profile = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("12000.00"), new BigDecimal("6000.00"), new BigDecimal("2000.00"),
                Instant.now()
        );

        when(goalDao.findById(goalId)).thenReturn(Optional.of(goal));
        when(profileDao.findByWalletId(walletId)).thenReturn(Optional.of(profile));
        when(balanceUseCase.getBalance(walletId)).thenReturn(new BigDecimal("20000.00"));

        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);
        GoalStrategyResponse response = service.calculateStrategy(goalId, evaluationDate);

        assertThat(response.goalId()).isEqualTo(goalId);
        assertThat(response.targetAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.currentAccumulatedAmount()).isEqualByComparingTo("20000.00");
        assertThat(response.remainingDeficit()).isEqualByComparingTo("80000.00");
        assertThat(response.safeMonthlyContributionCapacity()).isEqualByComparingTo("4000.00");
        assertThat(response.feasibility()).isEqualTo(GoalFeasibility.ON_TRACK);
    }

    @Test
    @DisplayName("Should simulate goal strategy statelessly without database writes (REQ-GOAL-012)")
    void shouldSimulateGoalStrategy() {
        SimulateGoalCommand command = new SimulateGoalCommand(
                new BigDecimal("50000.00"),
                LocalDate.of(2027, 12, 31),
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("6000.00"),
                new BigDecimal("2000.00")
        );

        LocalDate evaluationDate = LocalDate.of(2026, 8, 27);
        GoalStrategyResponse response = service.simulate(command, evaluationDate);

        assertThat(response.targetAmount()).isEqualByComparingTo("50000.00");
        assertThat(response.remainingDeficit()).isEqualByComparingTo("40000.00");
        assertThat(response.safeMonthlyContributionCapacity()).isEqualByComparingTo("2000.00");
        assertThat(response.feasibility()).isNotNull();

        verifyNoInteractions(goalDao);
        verifyNoInteractions(profileDao);
        verifyNoInteractions(balanceUseCase);
    }
}
