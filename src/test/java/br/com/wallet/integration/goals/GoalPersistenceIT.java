package br.com.wallet.integration.goals;

import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.goals.api.model.FinancialGoal;
import br.com.wallet.goals.api.model.GoalPriority;
import br.com.wallet.goals.api.model.GoalStatus;
import br.com.wallet.goals.internal.persistence.CashflowProfileDao;
import br.com.wallet.goals.internal.persistence.FinancialGoalDao;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Financial Goals & Cashflow Profile Persistence Integration Tests (PostgreSQL DAO Verification)")
public class GoalPersistenceIT extends DockerProperties {

    @Autowired
    private FinancialGoalDao goalDao;

    @Autowired
    private CashflowProfileDao cashflowProfileDao;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    private UUID walletA;
    private UUID walletB;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        walletA = UUID.randomUUID();
        walletB = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(walletA, userId);
        createWalletUseCase.handle(walletB, userId);
    }

    @Test
    @DisplayName("Should insert and retrieve a financial goal by ID and wallet ID")
    void shouldInsertAndRetrieveGoal() {
        UUID goalId = UUID.randomUUID();
        FinancialGoal goal = new FinancialGoal(
                goalId,
                userId,
                walletA,
                walletB,
                "Emergency Fund 2027",
                new BigDecimal("50000.00"),
                LocalDate.of(2027, 12, 31),
                GoalPriority.HIGH,
                GoalStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        goalDao.insert(goal);

        Optional<FinancialGoal> retrievedOpt = goalDao.findById(goalId);
        assertThat(retrievedOpt).isPresent();
        FinancialGoal retrieved = retrievedOpt.get();

        assertThat(retrieved.id()).isEqualTo(goalId);
        assertThat(retrieved.userId()).isEqualTo(userId);
        assertThat(retrieved.walletId()).isEqualTo(walletA);
        assertThat(retrieved.targetWalletId()).isEqualTo(walletB);
        assertThat(retrieved.name()).isEqualTo("Emergency Fund 2027");
        assertThat(retrieved.targetAmount()).isEqualByComparingTo("50000.00");
        assertThat(retrieved.targetDate()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(retrieved.priority()).isEqualTo(GoalPriority.HIGH);
        assertThat(retrieved.status()).isEqualTo(GoalStatus.ACTIVE);

        List<FinancialGoal> walletGoals = goalDao.findByWalletId(walletA);
        assertThat(walletGoals).hasSize(1);
        assertThat(walletGoals.getFirst().id()).isEqualTo(goalId);
    }

    @Test
    @DisplayName("Should update financial goal details and status")
    void shouldUpdateGoalAndStatus() {
        UUID goalId = UUID.randomUUID();
        FinancialGoal goal = new FinancialGoal(
                goalId,
                userId,
                walletA,
                null,
                "Old Name",
                new BigDecimal("10000.00"),
                LocalDate.of(2027, 6, 30),
                GoalPriority.LOW,
                GoalStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );
        goalDao.insert(goal);

        FinancialGoal updated = new FinancialGoal(
                goalId,
                userId,
                walletA,
                null,
                "New Name",
                new BigDecimal("25000.00"),
                LocalDate.of(2028, 1, 15),
                GoalPriority.CRITICAL,
                GoalStatus.ACTIVE,
                goal.createdAt(),
                Instant.now()
        );
        goalDao.update(updated);

        FinancialGoal retrieved = goalDao.findById(goalId).orElseThrow();
        assertThat(retrieved.name()).isEqualTo("New Name");
        assertThat(retrieved.targetAmount()).isEqualByComparingTo("25000.00");
        assertThat(retrieved.targetDate()).isEqualTo(LocalDate.of(2028, 1, 15));
        assertThat(retrieved.priority()).isEqualTo(GoalPriority.CRITICAL);

        goalDao.updateStatus(goalId, GoalStatus.PAUSED);
        assertThat(goalDao.findById(goalId).orElseThrow().status()).isEqualTo(GoalStatus.PAUSED);

        goalDao.updateStatus(goalId, GoalStatus.ACHIEVED);
        assertThat(goalDao.findById(goalId).orElseThrow().status()).isEqualTo(GoalStatus.ACHIEVED);
    }

    @Test
    @DisplayName("Should insert and upsert cashflow profile successfully")
    void shouldInsertAndUpsertCashflowProfile() {
        UUID profileId = UUID.randomUUID();
        CashflowProfile profile = new CashflowProfile(
                profileId,
                userId,
                walletA,
                new BigDecimal("10000.00"),
                new BigDecimal("6000.00"),
                new BigDecimal("2000.00"),
                Instant.now()
        );

        cashflowProfileDao.upsert(profile);

        Optional<CashflowProfile> retrievedOpt = cashflowProfileDao.findByWalletId(walletA);
        assertThat(retrievedOpt).isPresent();
        CashflowProfile retrieved = retrievedOpt.get();

        assertThat(retrieved.walletId()).isEqualTo(walletA);
        assertThat(retrieved.monthlyIncome()).isEqualByComparingTo("10000.00");
        assertThat(retrieved.monthlyCommittedExpenses()).isEqualByComparingTo("6000.00");
        assertThat(retrieved.minimumSafetyBuffer()).isEqualByComparingTo("2000.00");

        // Update with new income
        CashflowProfile updatedProfile = new CashflowProfile(
                profileId,
                userId,
                walletA,
                new BigDecimal("15000.00"),
                new BigDecimal("7000.00"),
                new BigDecimal("3000.00"),
                Instant.now()
        );
        cashflowProfileDao.upsert(updatedProfile);

        CashflowProfile updatedRetrieved = cashflowProfileDao.findByWalletId(walletA).orElseThrow();
        assertThat(updatedRetrieved.monthlyIncome()).isEqualByComparingTo("15000.00");
        assertThat(updatedRetrieved.monthlyCommittedExpenses()).isEqualByComparingTo("7000.00");
        assertThat(updatedRetrieved.minimumSafetyBuffer()).isEqualByComparingTo("3000.00");
    }
}
