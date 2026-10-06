package br.com.wallet.unit.infrastructure.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.fraud.intelligence.projector.RelationalGraphProjector;
import br.com.wallet.infrastructure.internal.listener.FraudGraphListener;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudGraphListener Unit Tests (REQ-STRM-001, REQ-STRM-005, I-STREAM-003)")
class FraudGraphListenerTest {

    @Mock
    private RelationalGraphProjector projector;

    @Mock
    private AccountUseCase accountUseCase;

    private FraudGraphListener listener;

    @BeforeEach
    void setUp() {
        listener = new FraudGraphListener(projector, accountUseCase);
    }

    @Test
    @DisplayName("REQ-STRM-001: Should project transfer on TransferCompletedEvent using resolved userIds")
    void shouldProjectTransferOnTransferCompletedEvent() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.00");

        Account sourceAccount = new Account(walletFrom, BigDecimal.valueOf(1000), 1L, userFrom, AccountStatus.ACTIVE, null, null, Instant.now(), "tenant-alpha");
        Account targetAccount = new Account(walletTo, BigDecimal.valueOf(500), 1L, userTo, AccountStatus.ACTIVE, null, null, Instant.now(), "tenant-alpha");

        when(accountUseCase.find(walletFrom)).thenReturn(sourceAccount);
        when(accountUseCase.find(walletTo)).thenReturn(targetAccount);

        TransferCompletedEvent event = new TransferCompletedEvent(
                walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        listener.onTransferCompleted(event);

        verify(projector, times(1)).projectTransfer(
                eq(walletFrom),
                eq(walletTo),
                eq(userFrom),
                eq(userTo),
                eq(amount),
                eq(operationId),
                any(Instant.class),
                eq("tenant-alpha")
        );
    }

    @Test
    @DisplayName("REQ-STRM-005 & I-STREAM-003: Duplicate event with same canonical identity must be ignored idempotently")
    void shouldIgnoreDuplicateEventId() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        Account sourceAccount = new Account(walletFrom, BigDecimal.valueOf(1000), 1L, userFrom, AccountStatus.ACTIVE, null, null, Instant.now(), "tenant-alpha");
        Account targetAccount = new Account(walletTo, BigDecimal.valueOf(500), 1L, userTo, AccountStatus.ACTIVE, null, null, Instant.now(), "tenant-alpha");

        when(accountUseCase.find(walletFrom)).thenReturn(sourceAccount);
        when(accountUseCase.find(walletTo)).thenReturn(targetAccount);

        TransferCompletedEvent event = new TransferCompletedEvent(
                walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        // First delivery
        listener.onTransferCompleted(event);
        verify(projector, times(1)).projectTransfer(any(), any(), any(), any(), any(), any(), any(), any());

        // Duplicate delivery
        listener.onTransferCompleted(event);
        // projector still only called once
        verify(projector, times(1)).projectTransfer(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
