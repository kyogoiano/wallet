package br.com.wallet.unit.ledger.service;

import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.ledger.api.domain.AccountBalance;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import br.com.wallet.ledger.api.exceptions.AccountNotFoundException;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.api.exceptions.UserNotAllowedException;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.WalletOperationService;
import br.com.wallet.ledger.internal.service.WithdrawFundsService;
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
@DisplayName("WithdrawFundsService Unit Tests")
class WithdrawFundsServiceTest {

    @Mock
    private WalletOperationService core;
    @Mock
    private WalletOperationsDao operationsDao;
    @Mock
    private OutboxDao outboxDao;
    @Mock
    private AccountDao accountDao;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneId.of("UTC"));
    private WithdrawFundsService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawFundsService(core, operationsDao, outboxDao, accountDao, clock);
    }

    @Test
    @DisplayName("Should successfully withdraw funds when balance is sufficient")
    void shouldWithdrawFundsSuccessfully() {
        final UUID walletId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(50.00);

        final Withdraw withdraw = new Withdraw(walletId, userId, amount, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findWalletBalanceForUpdate(walletId))
                .thenReturn(Optional.of(new AccountBalance(userId, BigDecimal.valueOf(100.00))));

        service.handle(withdraw);

        verify(core).applyTransaction(eq(walletId), eq(amount), eq(LedgerType.DEBIT), eq(opId), eq(userId), eq(clock.instant()));
        verify(outboxDao).save(any(WithdrawCompletedEvent.class));
        verify(operationsDao).completeOperation(opId);
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException when balance is too low")
    void shouldThrowWhenInsufficientFunds() {
        final UUID walletId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final BigDecimal amount = BigDecimal.valueOf(150.00);

        final Withdraw withdraw = new Withdraw(walletId, userId, amount, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findWalletBalanceForUpdate(walletId))
                .thenReturn(Optional.of(new AccountBalance(userId, BigDecimal.valueOf(50.00))));

        assertThatThrownBy(() -> service.handle(withdraw))
                .isInstanceOf(InsufficientFundsException.class);

        verifyNoInteractions(core);
    }

    @Test
    @DisplayName("Should throw UserNotAllowedException when withdrawer is not wallet owner")
    void shouldThrowWhenUserNotAllowed() {
        final UUID walletId = UUID.randomUUID();
        final UUID ownerUserId = UUID.randomUUID();
        final UUID otherUserId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();

        final Withdraw withdraw = new Withdraw(walletId, otherUserId, BigDecimal.TEN, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findWalletBalanceForUpdate(walletId))
                .thenReturn(Optional.of(new AccountBalance(ownerUserId, BigDecimal.valueOf(100.00))));

        assertThatThrownBy(() -> service.handle(withdraw))
                .isInstanceOf(UserNotAllowedException.class);
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when wallet does not exist")
    void shouldThrowWhenAccountNotFound() {
        final UUID walletId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Withdraw withdraw = new Withdraw(walletId, UUID.randomUUID(), BigDecimal.TEN, opId);

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findWalletBalanceForUpdate(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(withdraw))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
