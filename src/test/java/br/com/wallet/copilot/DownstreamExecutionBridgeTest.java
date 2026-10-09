package br.com.wallet.copilot;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.DownstreamStatus;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.ExecutionResult;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.api.exceptions.TransientException;
import br.com.wallet.goals.api.FinancialGoalUseCase;
import br.com.wallet.goals.api.dto.UpdateGoalCommand;
import br.com.wallet.goals.api.model.GoalPriority;
import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsRuleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DownstreamExecutionBridge Unit Tests (TASK-4.6, I-AI-009, I-AI-010)")
class DownstreamExecutionBridgeTest {

    @Mock
    private TransferFundsUseCase transferFundsUseCase;

    @Mock
    private OperationQueryUseCase operationQueryUseCase;

    @Mock
    private SavingsPlanUseCase savingsPlanUseCase;

    @Mock
    private FinancialGoalUseCase financialGoalUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DownstreamExecutionBridge bridge;

    private final UUID proposalId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final UUID toWalletId = UUID.randomUUID();
    private final UUID executionOpId = UUID.randomUUID();
    private final String tenantId = "tenant-bridge";

    @BeforeEach
    void setUp() {
        bridge = new DownstreamExecutionBridge(
                transferFundsUseCase,
                operationQueryUseCase,
                savingsPlanUseCase,
                financialGoalUseCase,
                objectMapper
        );
    }

    private FinancialProposal buildTransferProposal(String parametersJson) {
        Instant now = Instant.now();
        return new FinancialProposal(
                proposalId,
                tenantId,
                walletId,
                ProposalType.TRANSFER,
                parametersJson,
                "hash-123",
                ProposalStatus.EXECUTING,
                "key-1",
                executionOpId.toString(),
                "user-creator",
                "human-approver",
                now.minus(1, ChronoUnit.MINUTES),
                now.plus(15, ChronoUnit.MINUTES),
                now,
                now.plus(2, ChronoUnit.MINUTES),
                now,
                null,
                null
        );
    }

    @Test
    @DisplayName("Should successfully dispatch transfer downstream")
    void shouldSuccessfullyDispatchTransfer() {
        String json = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":150.00}", walletId, toWalletId);
        FinancialProposal proposal = buildTransferProposal(json);

        doNothing().when(transferFundsUseCase).handle(any());

        ExecutionResult result = bridge.dispatch(proposal);
        assertThat(result).isInstanceOf(ExecutionResult.Success.class);
        ExecutionResult.Success success = (ExecutionResult.Success) result;
        assertThat(success.executionReference()).isEqualTo(executionOpId.toString());
    }

    @Test
    @DisplayName("I-AI-010: InsufficientFundsException must be classified as BusinessFailure")
    void shouldClassifyInsufficientFundsAsBusinessFailure() {
        String json = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":500.00}", walletId, toWalletId);
        FinancialProposal proposal = buildTransferProposal(json);

        doThrow(new InsufficientFundsException("Insufficient balance"))
                .when(transferFundsUseCase).handle(any());

        ExecutionResult result = bridge.dispatch(proposal);
        assertThat(result).isInstanceOf(ExecutionResult.BusinessFailure.class);
        ExecutionResult.BusinessFailure failure = (ExecutionResult.BusinessFailure) result;
        assertThat(failure.reason()).contains("Insufficient balance");
    }

    @Test
    @DisplayName("I-AI-010: AccountBlockedException must be classified as BusinessFailure")
    void shouldClassifyAccountBlockedAsBusinessFailure() {
        String json = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":100.00}", walletId, toWalletId);
        FinancialProposal proposal = buildTransferProposal(json);

        doThrow(new AccountBlockedException("Account is blocked"))
                .when(transferFundsUseCase).handle(any());

        ExecutionResult result = bridge.dispatch(proposal);
        assertThat(result).isInstanceOf(ExecutionResult.BusinessFailure.class);
        ExecutionResult.BusinessFailure failure = (ExecutionResult.BusinessFailure) result;
        assertThat(failure.reason()).contains("Account is blocked");
    }

    @Test
    @DisplayName("I-AI-010: Transient technical failure must be classified as TechnicalFailure")
    void shouldClassifyTransientExceptionAsTechnicalFailure() {
        String json = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":100.00}", walletId, toWalletId);
        FinancialProposal proposal = buildTransferProposal(json);

        doThrow(new TransientException("Database connection timeout", new RuntimeException()))
                .when(transferFundsUseCase).handle(any());

        ExecutionResult result = bridge.dispatch(proposal);
        assertThat(result).isInstanceOf(ExecutionResult.TechnicalFailure.class);
    }

    @Test
    @DisplayName("Downstream status check: Operation COMPLETED returned as COMPLETED")
    void shouldReturnCompletedWhenDownstreamFound() {
        OperationStatusResponse response = new OperationStatusResponse(
                executionOpId,
                OperationStatus.COMPLETED,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
        when(operationQueryUseCase.getOperationStatus(executionOpId))
                .thenReturn(Optional.of(response));

        DownstreamStatus status = bridge.checkStatus(ProposalType.TRANSFER, executionOpId);
        assertThat(status).isEqualTo(DownstreamStatus.COMPLETED);
    }

    @Test
    @DisplayName("Downstream status check: Operation not found returned as NOT_EXECUTED")
    void shouldReturnNotExecutedWhenNotFound() {
        when(operationQueryUseCase.getOperationStatus(executionOpId))
                .thenReturn(Optional.empty());

        DownstreamStatus status = bridge.checkStatus(ProposalType.TRANSFER, executionOpId);
        assertThat(status).isEqualTo(DownstreamStatus.NOT_EXECUTED);
    }

    @Test
    @DisplayName("Downstream status check: Query exception returned as UNKNOWN")
    void shouldReturnUnknownOnQueryFailure() {
        when(operationQueryUseCase.getOperationStatus(executionOpId))
                .thenThrow(new QueryTimeoutException("Timeout"));

        DownstreamStatus status = bridge.checkStatus(ProposalType.TRANSFER, executionOpId);
        assertThat(status).isEqualTo(DownstreamStatus.UNKNOWN);
    }

    private FinancialProposal buildGoalAdjustmentProposal(String parametersJson) {
        Instant now = Instant.now();
        return new FinancialProposal(
                proposalId,
                tenantId,
                walletId,
                ProposalType.GOAL_ADJUSTMENT,
                parametersJson,
                "hash-goal",
                ProposalStatus.EXECUTING,
                "key-goal",
                executionOpId.toString(),
                "user-creator",
                "human-approver",
                now.minus(1, ChronoUnit.MINUTES),
                now.plus(15, ChronoUnit.MINUTES),
                now,
                now.plus(2, ChronoUnit.MINUTES),
                now,
                null,
                null
        );
    }

    private FinancialProposal buildSavingsRuleProposal(String parametersJson) {
        Instant now = Instant.now();
        return new FinancialProposal(
                proposalId,
                tenantId,
                walletId,
                ProposalType.SAVINGS_RULE,
                parametersJson,
                "hash-savings",
                ProposalStatus.EXECUTING,
                "key-savings",
                executionOpId.toString(),
                "user-creator",
                "human-approver",
                now.minus(1, ChronoUnit.MINUTES),
                now.plus(15, ChronoUnit.MINUTES),
                now,
                now.plus(2, ChronoUnit.MINUTES),
                now,
                null,
                null
        );
    }

    @Test
    @DisplayName("Should successfully dispatch goal adjustment with pre-parsed non-null parameters")
    void shouldSuccessfullyDispatchGoalAdjustment() {
        UUID goalId = UUID.randomUUID();
        LocalDate futureDate = LocalDate.now().plusMonths(6);
        String json = String.format("""
                {
                    "goalId": "%s",
                    "name": "Emergency Fund",
                    "targetAmount": "5000.00",
                    "targetDate": "%s",
                    "priority": "HIGH"
                }
                """, goalId, futureDate);

        FinancialProposal proposal = buildGoalAdjustmentProposal(json);
        ExecutionResult result = bridge.dispatch(proposal);

        assertThat(result).isInstanceOf(ExecutionResult.Success.class);
        ArgumentCaptor<UpdateGoalCommand> cmdCaptor = ArgumentCaptor.forClass(UpdateGoalCommand.class);
        verify(financialGoalUseCase).updateGoal(eq(goalId), cmdCaptor.capture());

        UpdateGoalCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.name()).isEqualTo("Emergency Fund");
        assertThat(cmd.targetAmount()).isEqualByComparingTo("5000.00");
        assertThat(cmd.targetDate()).isEqualTo(futureDate);
        assertThat(cmd.priority()).isEqualTo(GoalPriority.HIGH);
    }

    @Test
    @DisplayName("Goal adjustment with missing or blank name should fail as BusinessFailure")
    void shouldClassifyMissingNameAsBusinessFailure() {
        UUID goalId = UUID.randomUUID();
        String json = String.format("""
                {
                    "goalId": "%s",
                    "name": "   ",
                    "targetAmount": "5000.00",
                    "targetDate": "%s",
                    "priority": "HIGH"
                }
                """, goalId, LocalDate.now().plusMonths(6));

        FinancialProposal proposal = buildGoalAdjustmentProposal(json);
        ExecutionResult result = bridge.dispatch(proposal);

        assertThat(result).isInstanceOf(ExecutionResult.BusinessFailure.class);
        ExecutionResult.BusinessFailure failure = (ExecutionResult.BusinessFailure) result;
        assertThat(failure.reason()).contains("name");
    }

    @Test
    @DisplayName("Goal adjustment with past targetDate should fail as BusinessFailure")
    void shouldClassifyPastTargetDateAsBusinessFailure() {
        UUID goalId = UUID.randomUUID();
        String json = String.format("""
                {
                    "goalId": "%s",
                    "name": "Emergency Fund",
                    "targetAmount": "5000.00",
                    "targetDate": "%s",
                    "priority": "HIGH"
                }
                """, goalId, LocalDate.now().minusDays(1));

        FinancialProposal proposal = buildGoalAdjustmentProposal(json);
        ExecutionResult result = bridge.dispatch(proposal);

        assertThat(result).isInstanceOf(ExecutionResult.BusinessFailure.class);
        ExecutionResult.BusinessFailure failure = (ExecutionResult.BusinessFailure) result;
        assertThat(failure.reason()).contains("future date");
    }

    @Test
    @DisplayName("Goal adjustment with invalid targetAmount should fail as BusinessFailure")
    void shouldClassifyInvalidTargetAmountAsBusinessFailure() {
        UUID goalId = UUID.randomUUID();
        String json = String.format("""
                {
                    "goalId": "%s",
                    "name": "Emergency Fund",
                    "targetAmount": "0.00",
                    "targetDate": "%s",
                    "priority": "HIGH"
                }
                """, goalId, LocalDate.now().plusMonths(6));

        FinancialProposal proposal = buildGoalAdjustmentProposal(json);
        ExecutionResult result = bridge.dispatch(proposal);

        assertThat(result).isInstanceOf(ExecutionResult.BusinessFailure.class);
        ExecutionResult.BusinessFailure failure = (ExecutionResult.BusinessFailure) result;
        assertThat(failure.reason()).contains("targetAmount");
    }

    @Test
    @DisplayName("Transfer with missing 'to' should fail as BusinessFailure")
    void shouldClassifyMissingToAsBusinessFailure() {
        String json = "{\"amount\":100.00}";
        FinancialProposal proposal = buildTransferProposal(json);

        ExecutionResult result = bridge.dispatch(proposal);
        assertThat(result).isInstanceOf(ExecutionResult.BusinessFailure.class);
        ExecutionResult.BusinessFailure failure = (ExecutionResult.BusinessFailure) result;
        assertThat(failure.reason()).contains("to");
    }

    @Test
    @DisplayName("Should successfully dispatch savings rule with pre-parsed parameters")
    void shouldSuccessfullyDispatchSavingsRule() {
        UUID planId = UUID.randomUUID();
        String json = String.format("""
                {
                    "planId": "%s",
                    "ruleType": "PERCENTAGE",
                    "percentage": "10.00"
                }
                """, planId);

        FinancialProposal proposal = buildSavingsRuleProposal(json);
        ExecutionResult result = bridge.dispatch(proposal);

        assertThat(result).isInstanceOf(ExecutionResult.Success.class);
        verify(savingsPlanUseCase).addRule(eq(planId), any(CreateSavingsRuleCommand.class));
    }

    @Test
    @DisplayName("validateParameters directly asserts valid and invalid JSON structures")
    void shouldValidateParametersDirectly() {
        String validGoal = String.format("""
                {
                    "goalId": "%s",
                    "name": "Vacation",
                    "targetAmount": "1200.00",
                    "targetDate": "%s",
                    "priority": "MEDIUM"
                }
                """, UUID.randomUUID(), LocalDate.now().plusYears(1));

        // Valid should not throw
        bridge.validateParameters(ProposalType.GOAL_ADJUSTMENT, validGoal);

        // Missing field should throw IllegalArgumentException
        assertThatThrownBy(() -> bridge.validateParameters(ProposalType.GOAL_ADJUSTMENT, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goalId");

        // Malformed JSON should throw IllegalArgumentException
        assertThatThrownBy(() -> bridge.validateParameters(ProposalType.GOAL_ADJUSTMENT, "invalid json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
