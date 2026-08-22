package br.com.wallet.unit.wallet.service;

import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.wallet.api.context.Deposit;
import br.com.wallet.wallet.api.domain.LedgerType;
import br.com.wallet.wallet.api.event.DepositCompletedEvent;
import br.com.wallet.wallet.api.exceptions.AccountNotFoundException;
import br.com.wallet.wallet.api.guard.FraudCheckHelper;
import br.com.wallet.wallet.internal.persistence.AccountDao;
import br.com.wallet.wallet.internal.persistence.OutboxDao;
import br.com.wallet.wallet.internal.persistence.WalletOperationsDao;
import br.com.wallet.wallet.internal.service.DepositFundsService;
import br.com.wallet.wallet.internal.service.WalletOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositFundsService Unit Tests")
class DepositFundsServiceTest {

    @Mock
    private WalletOperationService core;
    @Mock
    private WalletOperationsDao operationsDao;
    @Mock
    private OutboxDao outboxDao;
    @Mock
    private AccountDao accountDao;
    @Mock
    private FraudCheckHelper fraudCheckHelper;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneId.of("UTC"));
    private DepositFundsService service;

    @BeforeEach
    void setUp() {
        service = new DepositFundsService(core, operationsDao, outboxDao, accountDao, fraudCheckHelper, clock);
    }

    @Test
    @DisplayName("Should successfully deposit funds into account")
    void shouldDepositFundsSuccessfully() {
        final UUID walletId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(250.00);

        final Deposit deposit = new Deposit(walletId, userId, amount, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);

        service.handle(deposit);

        verify(core).applyTransaction(eq(walletId), eq(amount), eq(LedgerType.CREDIT), eq(opId), eq(userId), eq(clock.instant()));
        verify(outboxDao).save(any(DepositCompletedEvent.class));
        verify(operationsDao).completeOperation(opId);
    }

    @Test
    @DisplayName("Should resolve userId from accountDao if not provided in Deposit")
    void shouldResolveUserIdFromDao() {
        final UUID walletId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(100.00);

        final Deposit deposit = new Deposit(walletId, null, amount, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findUserId(walletId)).thenReturn(Optional.of(userId));

        service.handle(deposit);

        verify(core).applyTransaction(eq(walletId), eq(amount), eq(LedgerType.CREDIT), eq(opId), eq(userId), eq(clock.instant()));
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException if wallet does not exist when resolving userId")
    void shouldThrowWhenAccountNotFound() {
        final UUID walletId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Deposit deposit = new Deposit(walletId, null, BigDecimal.TEN, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findUserId(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(deposit))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw IdempotencyException if operation already processed")
    void shouldThrowIdempotencyException() {
        final UUID opId = UUID.randomUUID();
        final Deposit deposit = new Deposit(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId);

        when(operationsDao.startOperation(opId)).thenReturn(false);

        assertThatThrownBy(() -> service.handle(deposit))
                .isInstanceOf(IdempotencyException.class);

        verifyNoInteractions(core);
    }
}
