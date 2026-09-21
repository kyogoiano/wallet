package br.com.wallet.unit.ledger.service;

import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.domain.AccountBalance;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.TransferFundsService;
import br.com.wallet.ledger.internal.service.WalletOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferFundsService Unit Tests")
class TransferFundsServiceTest {

    @Mock
    private WalletOperationService core;
    @Mock
    private OutboxDao<TransferCompletedEvent> outboxDao;
    @Mock
    private WalletOperationsDao operationsDao;
    @Mock
    private AccountDao accountDao;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private ApplicationEventPublisher publisher;

    private TransferFundsService service;

    @BeforeEach
    void setUp() {
        service = new TransferFundsService(core, outboxDao, operationsDao, accountDao, clock, publisher);
    }

    @Test
    @DisplayName("Should successfully transfer funds between two accounts")
    void shouldTransferFundsSuccessfully() {
        final UUID fromWallet = UUID.randomUUID();
        final UUID toWallet = UUID.randomUUID();
        final UUID fromUser = UUID.randomUUID();
        final UUID toUser = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(100.00);

        final Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId);

        when(operationsDao.startOperation(eq(opId), any())).thenReturn(true);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(fromUser, BigDecimal.valueOf(500.00)),
                toWallet, new AccountBalance(toUser, BigDecimal.valueOf(200.00))
        ));

        service.handle(transfer);

        verify(core).applyTransaction(eq(fromWallet), eq(amount), eq(LedgerType.DEBIT), eq(opId), eq(fromUser), eq(clock.instant()), eq("default"));
        verify(core).applyTransaction(eq(toWallet), eq(amount), eq(LedgerType.CREDIT), eq(opId), eq(toUser), eq(clock.instant()), eq("default"));
        verify(outboxDao).save(any(TransferCompletedEvent.class));
        verify(operationsDao).completeOperation(eq(opId), any());
    }

    @Test
    @DisplayName("Should reject transfer when amount is zero or negative")
    void shouldRejectZeroOrNegativeAmount() {
        final Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, UUID.randomUUID());

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(core);
    }

    @Test
    @DisplayName("Should throw IdempotencyException if operation is already COMPLETED")
    void shouldHandleIdempotencyWhenAlreadyCompleted() {
        final UUID opId = UUID.randomUUID();
        final Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId);

        when(operationsDao.startOperation(eq(opId), any())).thenReturn(false);
        when(operationsDao.getStatus(opId)).thenReturn(OperationStatus.COMPLETED);

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(IdempotencyException.class);

        verifyNoInteractions(core);
    }

    @Test
    @DisplayName("Should reject transfer to same wallet")
    void shouldRejectTransferToSameWallet() {
        final UUID walletId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Transfer transfer = new Transfer(walletId, walletId, BigDecimal.TEN, opId);

        when(operationsDao.startOperation(eq(opId), any())).thenReturn(true);

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transfer to same wallet");
    }

    @Test
    @DisplayName("Should reject transfer when funds are insufficient")
    void shouldRejectWhenInsufficientFunds() {
        final UUID fromWallet = UUID.randomUUID();
        final UUID toWallet = UUID.randomUUID();
        final UUID fromUser = UUID.randomUUID();
        final UUID toUser = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(500.00);

        final Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId);

        when(operationsDao.startOperation(eq(opId), any())).thenReturn(true);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(fromUser, BigDecimal.valueOf(50.00)),
                toWallet, new AccountBalance(toUser, BigDecimal.valueOf(200.00))
        ));

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(InsufficientFundsException.class);

        verifyNoInteractions(core);
    }

    @Test
    @DisplayName("Should propagate AccountBlockedException when account is BLOCKED (I-ACCOUNT-001)")
    void shouldPropagateAccountBlockedException() {
        final UUID fromWallet = UUID.randomUUID();
        final UUID toWallet = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(50.00);

        final Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId);

        when(operationsDao.startOperation(eq(opId), any())).thenReturn(true);
        when(accountDao.getBalancesFromWallets(any()))
                .thenThrow(new br.com.wallet.core.exceptions.AccountBlockedException(fromWallet, "Fraud suspicion"));

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(br.com.wallet.core.exceptions.AccountBlockedException.class);

        verifyNoInteractions(core);
    }

    @Test
    @DisplayName("TASK-SEC-5.2: Should reject cross-tenant transfer with TenantMismatchException without altering balances")
    void shouldRejectCrossTenantTransfer() {
        final UUID fromWallet = UUID.randomUUID();
        final UUID toWallet = UUID.randomUUID();
        final UUID fromUser = UUID.randomUUID();
        final UUID toUser = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(50.00);

        // Command belongs to tenant-A
        final Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId, br.com.wallet.core.context.OperationOrigin.USER, "tenant-A");

        when(operationsDao.startOperation(eq(opId), any())).thenReturn(true);
        // Source wallet belongs to tenant-A, but target wallet belongs to tenant-B!
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(fromUser, BigDecimal.valueOf(500.00), br.com.wallet.ledger.api.domain.AccountStatus.ACTIVE, "tenant-A"),
                toWallet, new AccountBalance(toUser, BigDecimal.valueOf(200.00), br.com.wallet.ledger.api.domain.AccountStatus.ACTIVE, "tenant-B")
        ));

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(br.com.wallet.core.exceptions.TenantMismatchException.class)
                .hasMessageContaining("Cross-tenant transfer is forbidden");

        verify(operationsDao).failOperation(eq(opId), contains("Cross-tenant"), eq("FORBIDDEN_TENANT_ACCESS"), eq("tenant-A"));
        verifyNoInteractions(core);
        verifyNoInteractions(outboxDao);
    }
}
