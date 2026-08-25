package br.com.wallet.unit.savings.service;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.savings.api.model.SavingsExecutionStatus;
import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.application.SavingsExecutionService;
import br.com.wallet.savings.internal.domain.IntendedSweepAction;
import br.com.wallet.savings.internal.persistence.SavingsExecutionHistoryDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsExecutionService Unit Tests")
class SavingsExecutionServiceTest {

    @Mock
    private TransferFundsUseCase transferFundsUseCase;
    @Mock
    private SavingsExecutionHistoryDao historyDao;

    @InjectMocks
    private SavingsExecutionService executionService;

    private final UUID planId = UUID.randomUUID();
    private final UUID ruleId = UUID.randomUUID();
    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();
    private final UUID sourceOpId = UUID.randomUUID();

    @Test
    @DisplayName("Should generate deterministic operationId using SHA-256")
    void shouldGenerateDeterministicOperationId() {
        UUID opId1 = executionService.generateSavingsOperationId(sourceOpId, ruleId);
        UUID opId2 = executionService.generateSavingsOperationId(sourceOpId, ruleId);

        assertThat(opId1).isNotNull();
        assertThat(opId1).isEqualTo(opId2);

        UUID differentOp = executionService.generateSavingsOperationId(UUID.randomUUID(), ruleId);
        assertThat(opId1).isNotEqualTo(differentOp);
    }

    @Test
    @DisplayName("Should execute sweep successfully and record EXECUTED status")
    void shouldExecuteSweepSuccessfully() {
        IntendedSweepAction action = new IntendedSweepAction(
                planId, ruleId, SavingsRuleType.PERCENTAGE, sourceWallet, targetWallet, new BigDecimal("500.00")
        );

        when(historyDao.isOperationProcessed(any())).thenReturn(false);

        executionService.executeSweep(action, sourceOpId, "DEPOSIT_COMPLETED");

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferFundsUseCase).handle(transferCaptor.capture());

        Transfer transfer = transferCaptor.getValue();
        assertThat(transfer.from()).isEqualTo(sourceWallet);
        assertThat(transfer.to()).isEqualTo(targetWallet);
        assertThat(transfer.amount()).isEqualByComparingTo("500.00");
        assertThat(transfer.origin()).isEqualTo(OperationOrigin.SAVINGS_AUTOMATION);

        verify(historyDao).insertExecution(
                eq(transfer.operationId()), eq(planId), eq(ruleId), eq(sourceOpId),
                eq("DEPOSIT_COMPLETED"), eq(new BigDecimal("500.00")), eq(new BigDecimal("500.00")),
                eq(SavingsExecutionStatus.EXECUTED), isNull()
        );
    }

    @Test
    @DisplayName("Should skip sweep if already processed (Layer 1 Deduplication)")
    void shouldSkipIfAlreadyProcessed() {
        IntendedSweepAction action = new IntendedSweepAction(
                planId, ruleId, SavingsRuleType.ROUND_UP, sourceWallet, targetWallet, new BigDecimal("2.70")
        );

        when(historyDao.isOperationProcessed(any())).thenReturn(true);

        executionService.executeSweep(action, sourceOpId, "TRANSFER_COMPLETED");

        verifyNoInteractions(transferFundsUseCase);
    }

    @Test
    @DisplayName("Should handle InsufficientFundsException gracefully and record SKIPPED_INSUFFICIENT_FUNDS")
    void shouldHandleInsufficientFundsGracefully() {
        IntendedSweepAction action = new IntendedSweepAction(
                planId, ruleId, SavingsRuleType.THRESHOLD, sourceWallet, targetWallet, new BigDecimal("4500.00")
        );

        when(historyDao.isOperationProcessed(any())).thenReturn(false);
        doThrow(new InsufficientFundsException()).when(transferFundsUseCase).handle(any());

        executionService.executeSweep(action, sourceOpId, "DEPOSIT_COMPLETED");

        verify(historyDao).insertExecution(
                any(), eq(planId), eq(ruleId), eq(sourceOpId),
                eq("DEPOSIT_COMPLETED"), eq(new BigDecimal("4500.00")), eq(BigDecimal.ZERO),
                eq(SavingsExecutionStatus.SKIPPED_INSUFFICIENT_FUNDS), any()
        );
    }

    @Test
    @DisplayName("Should handle fraud rejection gracefully and record REJECTED_BY_FRAUD")
    void shouldHandleFraudRejectionGracefully() {
        IntendedSweepAction action = new IntendedSweepAction(
                planId, ruleId, SavingsRuleType.PERCENTAGE, sourceWallet, targetWallet, new BigDecimal("100.00")
        );

        when(historyDao.isOperationProcessed(any())).thenReturn(false);
        doThrow(new AccountBlockedException("Blocked")).when(transferFundsUseCase).handle(any());

        executionService.executeSweep(action, sourceOpId, "DEPOSIT_COMPLETED");

        verify(historyDao).insertExecution(
                any(), eq(planId), eq(ruleId), eq(sourceOpId),
                eq("DEPOSIT_COMPLETED"), eq(new BigDecimal("100.00")), eq(BigDecimal.ZERO),
                eq(SavingsExecutionStatus.REJECTED_BY_FRAUD), any()
        );
    }
}
