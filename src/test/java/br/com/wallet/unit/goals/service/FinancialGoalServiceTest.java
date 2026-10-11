package br.com.wallet.unit.goals.service;

import br.com.wallet.goals.api.dto.CreateGoalCommand;
import br.com.wallet.goals.api.dto.GoalResponse;
import br.com.wallet.goals.api.dto.UpdateGoalCommand;
import br.com.wallet.goals.api.model.FinancialGoal;
import br.com.wallet.goals.api.model.GoalPriority;
import br.com.wallet.goals.api.model.GoalStatus;
import br.com.wallet.goals.internal.persistence.FinancialGoalDao;
import br.com.wallet.goals.internal.service.FinancialGoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("Unit Tests: FinancialGoalService")
class FinancialGoalServiceTest {

    private FinancialGoalDao goalDao;
    private FinancialGoalService service;

    @BeforeEach
    void setUp() {
        goalDao = mock(FinancialGoalDao.class);
        service = new FinancialGoalService(goalDao);
    }

    @Test
    @DisplayName("Should create financial goal successfully")
    void shouldCreateGoal() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        CreateGoalCommand command = new CreateGoalCommand(
                userId,
                walletId,
                null,
                "Emergency Fund",
                new BigDecimal("50000.00"),
                LocalDate.of(2028, 12, 31),
                GoalPriority.HIGH
        );

        GoalResponse response = service.createGoal(command);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Emergency Fund");
        assertThat(response.targetAmount()).isEqualByComparingTo("50000.00");
        assertThat(response.priority()).isEqualTo(GoalPriority.HIGH);
        assertThat(response.status()).isEqualTo(GoalStatus.ACTIVE);

        ArgumentCaptor<FinancialGoal> captor = ArgumentCaptor.forClass(FinancialGoal.class);
        verify(goalDao).insert(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Emergency Fund");
    }

    @Test
    @DisplayName("Should update financial goal successfully")
    void shouldUpdateGoal() {
        UUID goalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        FinancialGoal existing = new FinancialGoal(
                goalId, userId, walletId, null,
                "Old Name", new BigDecimal("10000.00"),
                LocalDate.of(2027, 1, 1), GoalPriority.LOW, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        when(goalDao.findById(goalId)).thenReturn(Optional.of(existing));

        UpdateGoalCommand updateCmd = new UpdateGoalCommand(
                "New Name",
                new BigDecimal("20000.00"),
                LocalDate.of(2028, 1, 1),
                GoalPriority.CRITICAL
        );

        GoalResponse response = service.updateGoal(goalId, updateCmd);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.targetAmount()).isEqualByComparingTo("20000.00");
        assertThat(response.priority()).isEqualTo(GoalPriority.CRITICAL);
        verify(goalDao).update(any(FinancialGoal.class));
    }

    @Test
    @DisplayName("Should pause, resume, cancel, and mark achieved")
    void shouldManageGoalLifecycle() {
        UUID goalId = UUID.randomUUID();
        FinancialGoal existing = new FinancialGoal(
                goalId, UUID.randomUUID(), UUID.randomUUID(), null,
                "Goal", new BigDecimal("10000.00"),
                LocalDate.of(2027, 1, 1), GoalPriority.MEDIUM, GoalStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        when(goalDao.findById(goalId)).thenReturn(Optional.of(existing));

        service.pauseGoal(goalId);
        verify(goalDao).updateStatus(goalId, GoalStatus.PAUSED);

        service.resumeGoal(goalId);
        verify(goalDao).updateStatus(goalId, GoalStatus.ACTIVE);

        service.cancelGoal(goalId);
        verify(goalDao).updateStatus(goalId, GoalStatus.CANCELLED);

        service.markAchieved(goalId);
        verify(goalDao).updateStatus(goalId, GoalStatus.ACHIEVED);
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when updating non-existent goal")
    void shouldThrowWhenGoalNotFound() {
        UUID goalId = UUID.randomUUID();
        when(goalDao.findById(goalId)).thenReturn(Optional.empty());

        UpdateGoalCommand updateCmd = new UpdateGoalCommand(
                "Name", new BigDecimal("1000.00"), LocalDate.of(2027, 1, 1), GoalPriority.LOW
        );

        assertThatThrownBy(() -> service.updateGoal(goalId, updateCmd))
                .isInstanceOf(NoSuchElementException.class);
    }
}
