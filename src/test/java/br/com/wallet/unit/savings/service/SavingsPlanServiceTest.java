package br.com.wallet.unit.savings.service;

import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.application.SavingsPlanService;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
import br.com.wallet.savings.internal.persistence.SavingsRuleDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsPlanService Unit Tests")
class SavingsPlanServiceTest {

    @Mock
    private SavingsPlanDao savingsPlanDao;

    @Mock
    private SavingsRuleDao savingsRuleDao;

    @InjectMocks
    private SavingsPlanService savingsPlanService;

    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();

    @Test
    @DisplayName("Should create savings plan with rules successfully")
    void shouldCreatePlanSuccessfully() {
        CreateSavingsPlanCommand command = new CreateSavingsPlanCommand(
                sourceWallet, targetWallet, new BigDecimal("100.00"),
                List.of(
                        new CreateSavingsRuleCommand(SavingsRuleType.PERCENTAGE, null, new BigDecimal("10.00"), null),
                        new CreateSavingsRuleCommand(SavingsRuleType.ROUND_UP, new BigDecimal("5.00"), null, null)
                )
        );

        SavingsPlanDto created = savingsPlanService.createPlan(command);

        assertThat(created).isNotNull();
        assertThat(created.sourceWalletId()).isEqualTo(sourceWallet);
        assertThat(created.targetWalletId()).isEqualTo(targetWallet);
        assertThat(created.minimumRetainedBalance()).isEqualByComparingTo("100.00");
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.rules()).hasSize(2);

        ArgumentCaptor<SavingsPlan> planCaptor = ArgumentCaptor.forClass(SavingsPlan.class);
        verify(savingsPlanDao).insert(planCaptor.capture());
        assertThat(planCaptor.getValue().sourceWalletId()).isEqualTo(sourceWallet);
    }

    @Test
    @DisplayName("Should reject creating plan with identical source and target wallets")
    void shouldRejectIdenticalWallets() {
        CreateSavingsPlanCommand command = new CreateSavingsPlanCommand(
                sourceWallet, sourceWallet, BigDecimal.ZERO, List.of()
        );

        assertThatThrownBy(() -> savingsPlanService.createPlan(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source and target wallets must be different");
    }

    @Test
    @DisplayName("Should get plan by ID")
    void shouldGetPlanById() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(SavingsRule.percentage(ruleId, planId, new BigDecimal("10.00"))),
                Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findById(planId)).thenReturn(Optional.of(plan));

        SavingsPlanDto result = savingsPlanService.getPlan(planId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(planId);
        assertThat(result.rules()).hasSize(1);
    }

    @Test
    @DisplayName("Should throw when plan not found by ID")
    void shouldThrowWhenPlanNotFound() {
        when(savingsPlanDao.findById(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> savingsPlanService.getPlan(planId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Savings plan not found");
    }

    @Test
    @DisplayName("Should pause, resume, and delete plan")
    void shouldManagePlanLifecycle() {
        savingsPlanService.pausePlan(planId);
        verify(savingsPlanDao).updateStatus(planId, "PAUSED");

        savingsPlanService.resumePlan(planId);
        verify(savingsPlanDao).updateStatus(planId, "ACTIVE");

        savingsPlanService.deletePlan(planId);
        verify(savingsPlanDao).delete(planId);
    }

    @Test
    @DisplayName("Should get plans by source wallet")
    void shouldGetPlansBySourceWallet() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(SavingsRule.percentage(ruleId, planId, new BigDecimal("10.00"))),
                Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findBySourceWalletId(sourceWallet)).thenReturn(List.of(plan));

        List<SavingsPlanDto> plans = savingsPlanService.getPlansBySourceWallet(sourceWallet);

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().id()).isEqualTo(planId);
        verify(savingsPlanDao).findBySourceWalletId(sourceWallet);
    }

    @Test
    @DisplayName("Should get plans by target wallet")
    void shouldGetPlansByTargetWallet() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(SavingsRule.percentage(ruleId, planId, new BigDecimal("10.00"))),
                Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findByTargetWalletId(targetWallet)).thenReturn(List.of(plan));

        List<SavingsPlanDto> plans = savingsPlanService.getPlansByTargetWallet(targetWallet);

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().id()).isEqualTo(planId);
        verify(savingsPlanDao).findByTargetWalletId(targetWallet);
    }

    @Test
    @DisplayName("Should list all plans with pagination")
    void shouldListPlansWithPagination() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findAll(50, 10)).thenReturn(List.of(plan));

        List<SavingsPlanDto> plans = savingsPlanService.listPlans(50, 10);

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().id()).isEqualTo(planId);
        verify(savingsPlanDao).findAll(50, 10);
    }

    @Test
    @DisplayName("Should get all plans for wallet (source or target)")
    void shouldGetPlansForWallet() {
        SavingsPlan plan1 = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findByWalletId(sourceWallet)).thenReturn(List.of(plan1));

        List<SavingsPlanDto> plans = savingsPlanService.getPlansForWallet(sourceWallet);

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().id()).isEqualTo(planId);
        verify(savingsPlanDao).findByWalletId(sourceWallet);
    }

    @Test
    @DisplayName("Should dynamically add rule to existing plan")
    void shouldAddRuleToExistingPlan() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findById(planId)).thenReturn(Optional.of(plan));

        CreateSavingsRuleCommand command = new CreateSavingsRuleCommand(
                SavingsRuleType.ROUND_UP, new BigDecimal("2.00"), null, null
        );

        SavingsRuleDto ruleDto = savingsPlanService.addRule(planId, command);

        assertThat(ruleDto).isNotNull();
        assertThat(ruleDto.planId()).isEqualTo(planId);
        assertThat(ruleDto.ruleType()).isEqualTo(SavingsRuleType.ROUND_UP);
        assertThat(ruleDto.stepAmount()).isEqualTo(new BigDecimal("2.00"));
        assertThat(ruleDto.isActive()).isTrue();

        verify(savingsRuleDao).insert(any(SavingsRule.class));
        verify(savingsPlanDao).touchUpdatedAt(planId);
    }

    @Test
    @DisplayName("Should reject invalid rule parameters when adding rule")
    void shouldRejectInvalidRuleParameters() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findById(planId)).thenReturn(Optional.of(plan));

        // Invalid round up
        CreateSavingsRuleCommand invalidRoundUp = new CreateSavingsRuleCommand(
                SavingsRuleType.ROUND_UP, new BigDecimal("-1.00"), null, null
        );
        assertThatThrownBy(() -> savingsPlanService.addRule(planId, invalidRoundUp))
                .isInstanceOf(IllegalArgumentException.class);

        // Invalid percentage (greater than 100)
        CreateSavingsRuleCommand invalidPercentage = new CreateSavingsRuleCommand(
                SavingsRuleType.PERCENTAGE, null, new BigDecimal("105.00"), null
        );
        assertThatThrownBy(() -> savingsPlanService.addRule(planId, invalidPercentage))
                .isInstanceOf(IllegalArgumentException.class);

        // Invalid threshold (zero or negative)
        CreateSavingsRuleCommand invalidThreshold = new CreateSavingsRuleCommand(
                SavingsRuleType.THRESHOLD, null, null, BigDecimal.ZERO
        );
        assertThatThrownBy(() -> savingsPlanService.addRule(planId, invalidThreshold))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should remove rule by ID")
    void shouldRemoveRule() {
        SavingsRule rule = SavingsRule.roundUp(ruleId, planId, new BigDecimal("5.00"));
        when(savingsRuleDao.findById(ruleId)).thenReturn(Optional.of(rule));

        savingsPlanService.removeRule(ruleId);

        verify(savingsRuleDao).deleteById(ruleId);
        verify(savingsPlanDao).touchUpdatedAt(planId);
    }

    @Test
    @DisplayName("Should toggle rule status by ID")
    void shouldToggleRule() {
        SavingsRule rule = SavingsRule.roundUp(ruleId, planId, new BigDecimal("5.00"));
        when(savingsRuleDao.findById(ruleId)).thenReturn(Optional.of(rule));

        savingsPlanService.toggleRule(ruleId, false);

        verify(savingsRuleDao).updateActive(ruleId, false);
        verify(savingsPlanDao).touchUpdatedAt(planId);
    }

    @Test
    @DisplayName("Should get all rules for plan")
    void shouldGetRulesForPlan() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(), Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findById(planId)).thenReturn(Optional.of(plan));

        SavingsRule rule1 = SavingsRule.roundUp(UUID.randomUUID(), planId, new BigDecimal("5.00"));
        SavingsRule rule2 = SavingsRule.percentage(UUID.randomUUID(), planId, new BigDecimal("10.00"));
        when(savingsRuleDao.findByPlanId(planId)).thenReturn(List.of(rule1, rule2));

        List<SavingsRuleDto> rules = savingsPlanService.getRulesForPlan(planId);

        assertThat(rules).hasSize(2);
    }
}
