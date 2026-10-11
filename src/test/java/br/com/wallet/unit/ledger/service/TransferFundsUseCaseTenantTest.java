package br.com.wallet.unit.ledger.service;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantMismatchException;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.domain.AccountBalance;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
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
@DisplayName("TransferFundsUseCaseTenantTest (I-SEC-005, TASK-SEC-5.3)")
class TransferFundsUseCaseTenantTest {

    @Mock
    private WalletOperationService core;
    @Mock
    private OutboxDao<TransferCompletedEvent> outboxDao;
    @Mock
    private WalletOperationsDao operationsDao;
    @Mock
    private AccountDao accountDao;
    @Mock
    private ApplicationEventPublisher publisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneId.of("UTC"));
    private TransferFundsService service;

    @BeforeEach
    void setUp() {
        service = new TransferFundsService(core, outboxDao, operationsDao, accountDao, clock, publisher);
    }

    @Test
    @DisplayName("Positive test: same-tenant accounts transfer funds successfully")
    void shouldTransferFundsSuccessfullyWithinSameTenant() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID fromUser = UUID.randomUUID();
        UUID toUser = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100.00);
        String tenantId = "tenant-corp-acme";

        Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId, OperationOrigin.USER, tenantId);

        when(operationsDao.startOperation(eq(opId), eq(tenantId))).thenReturn(true);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(fromUser, BigDecimal.valueOf(500.00), AccountStatus.ACTIVE, tenantId),
                toWallet, new AccountBalance(toUser, BigDecimal.valueOf(200.00), AccountStatus.ACTIVE, tenantId)
        ));

        service.handle(transfer);

        verify(core).applyTransaction(eq(fromWallet), eq(amount), eq(LedgerType.DEBIT), eq(opId), eq(fromUser), eq(clock.instant()), eq("tenant-corp-acme"));
        verify(core).applyTransaction(eq(toWallet), eq(amount), eq(LedgerType.CREDIT), eq(opId), eq(toUser), eq(clock.instant()), eq("tenant-corp-acme"));
        verify(outboxDao).save(any(TransferCompletedEvent.class));
        verify(operationsDao).completeOperation(eq(opId), eq(tenantId));
    }

    @Test
    @DisplayName("Invariant breach: source account belongs to different tenant -> rejected with TenantMismatchException")
    void shouldRejectWhenSourceAccountTenantMismatch() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID fromUser = UUID.randomUUID();
        UUID toUser = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100.00);

        Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId, OperationOrigin.USER, "tenant-A");

        when(operationsDao.startOperation(eq(opId), eq("tenant-A"))).thenReturn(true);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(fromUser, BigDecimal.valueOf(500.00), AccountStatus.ACTIVE, "tenant-B"),
                toWallet, new AccountBalance(toUser, BigDecimal.valueOf(200.00), AccountStatus.ACTIVE, "tenant-A")
        ));

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(TenantMismatchException.class)
                .hasMessageContaining("Cross-tenant transfer is forbidden");

        // Assert no ledger entries, balance modifications, or outbox events occurred
        verifyNoInteractions(core);
        verifyNoInteractions(outboxDao);
        verify(operationsDao).failOperation(eq(opId), contains("Cross-tenant"), eq("FORBIDDEN_TENANT_ACCESS"), eq("tenant-A"));
    }

    @Test
    @DisplayName("Invariant breach: destination account belongs to different tenant -> rejected with TenantMismatchException")
    void shouldRejectWhenDestinationAccountTenantMismatch() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID fromUser = UUID.randomUUID();
        UUID toUser = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(100.00);

        Transfer transfer = new Transfer(fromWallet, toWallet, amount, opId, OperationOrigin.USER, "tenant-Alpha");

        when(operationsDao.startOperation(eq(opId), eq("tenant-Alpha"))).thenReturn(true);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(fromUser, BigDecimal.valueOf(500.00), AccountStatus.ACTIVE, "tenant-Alpha"),
                toWallet, new AccountBalance(toUser, BigDecimal.valueOf(200.00), AccountStatus.ACTIVE, "tenant-Beta")
        ));

        assertThatThrownBy(() -> service.handle(transfer))
                .isInstanceOf(TenantMismatchException.class)
                .hasMessageContaining("Cross-tenant transfer is forbidden");

        verifyNoInteractions(core);
        verifyNoInteractions(outboxDao);
        verify(operationsDao).failOperation(eq(opId), contains("Cross-tenant"), eq("FORBIDDEN_TENANT_ACCESS"), eq("tenant-Alpha"));
    }
}
