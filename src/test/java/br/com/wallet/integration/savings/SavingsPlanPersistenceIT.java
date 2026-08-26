package br.com.wallet.integration.savings;

import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
import br.com.wallet.savings.internal.persistence.SavingsRuleDao;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Savings Plan & Rule Persistence Integration Tests (PostgreSQL DAO Verification)")
public class SavingsPlanPersistenceIT extends DockerProperties {

    @Autowired
    private SavingsPlanDao savingsPlanDao;

    @Autowired
    private SavingsRuleDao savingsRuleDao;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    private UUID walletA;
    private UUID walletB;
    private UUID walletC;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        walletA = UUID.randomUUID();
        walletB = UUID.randomUUID();
        walletC = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(walletA, userId);
        createWalletUseCase.handle(walletB, userId);
        createWalletUseCase.handle(walletC, userId);
    }

    @Test
    @DisplayName("Should strictly isolate source vs target wallet queries in PostgreSQL")
    void shouldStrictlyIsolateSourceVsTargetWalletQueries() {
        UUID planId1 = UUID.randomUUID();
        UUID planId2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Plan 1: walletA (source) -> walletB (target)
        SavingsPlan plan1 = new SavingsPlan(
                planId1, walletA, walletB, new BigDecimal("50.00"), "ACTIVE",
                List.of(SavingsRule.roundUp(UUID.randomUUID(), planId1, new BigDecimal("5.00"))),
                now, now
        );
        savingsPlanDao.insert(plan1);

        // Plan 2: walletB (source) -> walletC (target)
        SavingsPlan plan2 = new SavingsPlan(
                planId2, walletB, walletC, BigDecimal.ZERO, "ACTIVE",
                List.of(SavingsRule.percentage(UUID.randomUUID(), planId2, new BigDecimal("10.00"))),
                now, now
        );
        savingsPlanDao.insert(plan2);

        // 1. Query by Source Wallet
        List<SavingsPlan> sourcePlansA = savingsPlanDao.findBySourceWalletId(walletA);
        assertThat(sourcePlansA).hasSize(1);
        assertThat(sourcePlansA.getFirst().id()).isEqualTo(planId1);
        assertThat(sourcePlansA.getFirst().sourceWalletId()).isEqualTo(walletA);
        assertThat(sourcePlansA.getFirst().targetWalletId()).isEqualTo(walletB);

        List<SavingsPlan> sourcePlansB = savingsPlanDao.findBySourceWalletId(walletB);
        assertThat(sourcePlansB).hasSize(1);
        assertThat(sourcePlansB.getFirst().id()).isEqualTo(planId2);
        assertThat(sourcePlansB.getFirst().sourceWalletId()).isEqualTo(walletB);

        List<SavingsPlan> sourcePlansC = savingsPlanDao.findBySourceWalletId(walletC);
        assertThat(sourcePlansC).isEmpty();

        // 2. Query by Target Wallet
        List<SavingsPlan> targetPlansA = savingsPlanDao.findByTargetWalletId(walletA);
        assertThat(targetPlansA).isEmpty();

        List<SavingsPlan> targetPlansB = savingsPlanDao.findByTargetWalletId(walletB);
        assertThat(targetPlansB).hasSize(1);
        assertThat(targetPlansB.getFirst().id()).isEqualTo(planId1);

        List<SavingsPlan> targetPlansC = savingsPlanDao.findByTargetWalletId(walletC);
        assertThat(targetPlansC).hasSize(1);
        assertThat(targetPlansC.getFirst().id()).isEqualTo(planId2);

        // 3. Query by General Wallet (source or target)
        List<SavingsPlan> walletPlansB = savingsPlanDao.findByWalletId(walletB);
        assertThat(walletPlansB).hasSize(2);
        assertThat(walletPlansB.stream().map(SavingsPlan::id)).containsExactlyInAnyOrder(planId1, planId2);

        List<SavingsPlan> walletPlansA = savingsPlanDao.findByWalletId(walletA);
        assertThat(walletPlansA).hasSize(1);
        assertThat(walletPlansA.getFirst().id()).isEqualTo(planId1);
    }

    @Test
    @DisplayName("Should query active plans by source and target wallet")
    void shouldQueryActivePlans() {
        UUID planId1 = UUID.randomUUID();
        UUID planId2 = UUID.randomUUID();
        Instant now = Instant.now();

        SavingsPlan plan1 = new SavingsPlan(
                planId1, walletA, walletB, BigDecimal.ZERO, "ACTIVE",
                List.of(), now, now
        );
        savingsPlanDao.insert(plan1);

        SavingsPlan plan2 = new SavingsPlan(
                planId2, walletA, walletC, BigDecimal.ZERO, "PAUSED",
                List.of(), now, now
        );
        savingsPlanDao.insert(plan2);

        List<SavingsPlan> activeSourcePlans = savingsPlanDao.findActiveBySourceWalletId(walletA);
        assertThat(activeSourcePlans).hasSize(1);
        assertThat(activeSourcePlans.getFirst().id()).isEqualTo(planId1);

        List<SavingsPlan> activeTargetPlans = savingsPlanDao.findActiveByTargetWalletId(walletB);
        assertThat(activeTargetPlans).hasSize(1);
        assertThat(activeTargetPlans.getFirst().id()).isEqualTo(planId1);

        List<SavingsPlan> activeTargetPaused = savingsPlanDao.findActiveByTargetWalletId(walletC);
        assertThat(activeTargetPaused).isEmpty();
    }

    @Test
    @DisplayName("Should execute SavingsRuleDao CRUD operations in PostgreSQL")
    void shouldExecuteRuleDaoCrud() {
        UUID planId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        Instant now = Instant.now();

        SavingsPlan plan = new SavingsPlan(
                planId, walletA, walletB, BigDecimal.ZERO, "ACTIVE",
                List.of(), now, now
        );
        savingsPlanDao.insert(plan);

        // 1. Insert rule
        SavingsRule rule = new SavingsRule(
                ruleId, planId, SavingsRuleType.THRESHOLD, null, null, new BigDecimal("5000.00"), true
        );
        savingsRuleDao.insert(rule);

        // 2. Find by ID
        Optional<SavingsRule> found = savingsRuleDao.findById(ruleId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(ruleId);
        assertThat(found.get().ruleType()).isEqualTo(SavingsRuleType.THRESHOLD);
        assertThat(found.get().ceilingThreshold()).isEqualByComparingTo("5000.00");
        assertThat(found.get().isActive()).isTrue();

        // 3. Update active status
        savingsRuleDao.updateActive(ruleId, false);
        Optional<SavingsRule> updated = savingsRuleDao.findById(ruleId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isActive()).isFalse();

        // 4. Delete rule by ID
        savingsRuleDao.deleteById(ruleId);
        assertThat(savingsRuleDao.findById(ruleId)).isEmpty();
    }

    @Test
    @DisplayName("Should list all savings plans with pagination from PostgreSQL")
    void shouldListAllPlansWithPagination() {
        UUID planId1 = UUID.randomUUID();
        UUID planId2 = UUID.randomUUID();
        Instant now = Instant.now();

        savingsPlanDao.insert(new SavingsPlan(planId1, walletA, walletB, BigDecimal.ZERO, "ACTIVE", List.of(), now, now));
        savingsPlanDao.insert(new SavingsPlan(planId2, walletB, walletC, BigDecimal.ZERO, "ACTIVE", List.of(), now, now));

        List<SavingsPlan> page1 = savingsPlanDao.findAll(1, 0);
        assertThat(page1).hasSize(1);

        List<SavingsPlan> all = savingsPlanDao.findAll(10, 0);
        assertThat(all).hasSize(2);
    }
}
