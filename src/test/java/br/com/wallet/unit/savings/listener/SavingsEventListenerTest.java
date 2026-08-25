package br.com.wallet.unit.savings.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.event.DepositCompletedEvent;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.application.SavingsExecutionService;
import br.com.wallet.savings.internal.domain.IntendedSweepAction;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.engine.SavingsRuleEngine;
import br.com.wallet.savings.internal.listener.SavingsEventListener;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsEventListener Unit Tests")
class SavingsEventListenerTest {

    @Mock
    private SavingsPlanDao savingsPlanDao;
    @Mock
    private SavingsRuleEngine ruleEngine;
    @Mock
    private SavingsExecutionService executionService;
    @Mock
    private BalanceUseCase balanceUseCase;

    @InjectMocks
    private SavingsEventListener listener;

    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID opId = UUID.randomUUID();

    private SavingsPlan activePlan;

    @BeforeEach
    void setUp() {
        SavingsRule rule = SavingsRule.percentage(UUID.randomUUID(), planId, new BigDecimal("10.00"));
        activePlan = new SavingsPlan(
                planId, sourceWallet, targetWallet, BigDecimal.ZERO, "ACTIVE",
                List.of(rule), Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("Should immediately ignore deposit event with origin != USER (Loop Guard)")
    void shouldIgnoreDepositEventWhenOriginNotUser() {
        DepositCompletedEvent event = new DepositCompletedEvent(
                sourceWallet, new BigDecimal("100.00"), opId, OperationOrigin.SAVINGS_AUTOMATION
        );

        listener.onDeposit(event);

        verifyNoInteractions(savingsPlanDao, ruleEngine, executionService, balanceUseCase);
    }

    @Test
    @DisplayName("Should immediately ignore transfer event with origin != USER (Loop Guard I-SAVINGS-001)")
    void shouldIgnoreTransferEventWhenOriginNotUser() {
        TransferCompletedEvent event = new TransferCompletedEvent(
                sourceWallet, targetWallet, new BigDecimal("50.00"), opId, OperationOrigin.SAVINGS_AUTOMATION
        );

        listener.onTransfer(event);

        verifyNoInteractions(savingsPlanDao, ruleEngine, executionService, balanceUseCase);
    }

    @Test
    @DisplayName("Should process USER deposit event and execute evaluated sweep actions")
    void shouldProcessUserDepositEvent() {
        DepositCompletedEvent event = new DepositCompletedEvent(
                sourceWallet, new BigDecimal("5000.00"), opId, OperationOrigin.USER
        );

        when(savingsPlanDao.findActiveBySourceWalletId(sourceWallet)).thenReturn(List.of(activePlan));
        when(balanceUseCase.getBalance(sourceWallet)).thenReturn(new BigDecimal("5000.00"));

        IntendedSweepAction action = new IntendedSweepAction(
                planId, activePlan.rules().getFirst().id(), SavingsRuleType.PERCENTAGE,
                sourceWallet, targetWallet, new BigDecimal("500.00")
        );
        when(ruleEngine.evaluateDeposit(activePlan, new BigDecimal("5000.00"), new BigDecimal("5000.00")))
                .thenReturn(List.of(action));

        listener.onDeposit(event);

        verify(executionService).executeSweep(action, opId, "DEPOSIT_COMPLETED");
    }

    @Test
    @DisplayName("Should process USER transfer event and execute evaluated round-up actions")
    void shouldProcessUserTransferEvent() {
        TransferCompletedEvent event = new TransferCompletedEvent(
                sourceWallet, UUID.randomUUID(), new BigDecimal("47.30"), opId, OperationOrigin.USER
        );

        when(savingsPlanDao.findActiveBySourceWalletId(sourceWallet)).thenReturn(List.of(activePlan));
        when(balanceUseCase.getBalance(sourceWallet)).thenReturn(new BigDecimal("100.00"));

        IntendedSweepAction action = new IntendedSweepAction(
                planId, activePlan.rules().getFirst().id(), SavingsRuleType.ROUND_UP,
                sourceWallet, targetWallet, new BigDecimal("2.70")
        );
        when(ruleEngine.evaluateTransfer(activePlan, new BigDecimal("47.30"), new BigDecimal("100.00")))
                .thenReturn(List.of(action));

        listener.onTransfer(event);

        verify(executionService).executeSweep(action, opId, "TRANSFER_COMPLETED");
    }
}
