package br.com.wallet.unit.savings.service;

import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.application.SavingsPlanService;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsPlanService Unit Tests")
class SavingsPlanServiceTest {

    @Mock
    private SavingsPlanDao savingsPlanDao;

    @InjectMocks
    private SavingsPlanService savingsPlanService;

    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();

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
    @DisplayName("Should get plans for wallet")
    void shouldGetPlansForWallet() {
        SavingsPlan plan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(SavingsRule.percentage(UUID.randomUUID(), planId, new BigDecimal("10.00"))),
                Instant.now(), Instant.now()
        );
        when(savingsPlanDao.findBySourceWalletId(sourceWallet)).thenReturn(List.of(plan));

        List<SavingsPlanDto> plans = savingsPlanService.getPlansForWallet(sourceWallet);

        assertThat(plans).hasSize(1);
        assertThat(plans.getFirst().id()).isEqualTo(planId);
    }
}
