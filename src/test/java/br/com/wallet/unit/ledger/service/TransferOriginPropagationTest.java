package br.com.wallet.unit.ledger.service;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountBalance;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.event.DepositCompletedEvent;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.DepositFundsService;
import br.com.wallet.ledger.internal.service.TransferFundsService;
import br.com.wallet.ledger.internal.service.WalletOperationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ledger Origin Propagation — Contract Tests")
class TransferOriginPropagationTest {

    @Mock
    private AccountDao accountDao;
    @Mock
    private OutboxDao outboxDao;
    @Mock
    private WalletOperationsDao operationsDao;
    @Mock
    private WalletOperationService walletOperationService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private TransferFundsService transferFundsService;
    @InjectMocks
    private DepositFundsService depositFundsService;

    private final UUID fromWallet = UUID.randomUUID();
    private final UUID toWallet = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Should propagate USER origin from Transfer command to TransferCompletedEvent")
    void shouldPropagateUserOriginOnTransfer() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(fromWallet, toWallet, new BigDecimal("100.00"), opId, OperationOrigin.USER);
        when(operationsDao.startOperation(opId)).thenReturn(false);
        when(operationsDao.getStatus(opId)).thenReturn(OperationStatus.PROCESSING);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(userId, new BigDecimal("500.00"), AccountStatus.ACTIVE),
                toWallet, new AccountBalance(userId, new BigDecimal("200.00"), AccountStatus.ACTIVE))
        );

        transferFundsService.handle(transfer);

        ArgumentCaptor<TransferCompletedEvent> eventCaptor = ArgumentCaptor.forClass(TransferCompletedEvent.class);
        verify(outboxDao).save(eventCaptor.capture());

        TransferCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.origin()).isEqualTo(OperationOrigin.USER);
        assertThat(publishedEvent.from()).isEqualTo(fromWallet);
        assertThat(publishedEvent.to()).isEqualTo(toWallet);
        assertThat(publishedEvent.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Should propagate SAVINGS_AUTOMATION origin from Transfer command to TransferCompletedEvent")
    void shouldPropagateSavingsAutomationOriginOnTransfer() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(fromWallet, toWallet, new BigDecimal("25.50"), opId, OperationOrigin.SAVINGS_AUTOMATION);

        when(operationsDao.getStatus(opId)).thenReturn(OperationStatus.FAILED);
        when(accountDao.getBalancesFromWallets(any())).thenReturn(Map.of(
                fromWallet, new AccountBalance(userId, new BigDecimal("500.00"), AccountStatus.ACTIVE),
                toWallet, new AccountBalance(userId, new BigDecimal("200.00"), AccountStatus.ACTIVE)
        ));

        transferFundsService.handle(transfer);

        ArgumentCaptor<TransferCompletedEvent> eventCaptor = ArgumentCaptor.forClass(TransferCompletedEvent.class);
        verify(outboxDao).save(eventCaptor.capture());

        TransferCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.origin()).isEqualTo(OperationOrigin.SAVINGS_AUTOMATION);
    }

    @Test
    @DisplayName("Should propagate USER origin from Deposit command to DepositCompletedEvent")
    void shouldPropagateUserOriginOnDeposit() {
        UUID opId = UUID.randomUUID();
        Deposit deposit = new Deposit(fromWallet, userId, new BigDecimal("1000.00"), opId, OperationOrigin.USER);
        Account account = new Account(fromWallet, BigDecimal.ZERO, 1L, userId, Instant.now());

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findAccount(deposit.walletId())).thenReturn(Optional.of(account));
        depositFundsService.handle(deposit);

        ArgumentCaptor<DepositCompletedEvent> eventCaptor = ArgumentCaptor.forClass(DepositCompletedEvent.class);
        verify(outboxDao).save(eventCaptor.capture());

        DepositCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.origin()).isEqualTo(OperationOrigin.USER);
        assertThat(publishedEvent.walletId()).isEqualTo(fromWallet);
        assertThat(publishedEvent.amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Should propagate SYSTEM origin from Deposit command to DepositCompletedEvent")
    void shouldPropagateSystemOriginOnDeposit() {
        UUID opId = UUID.randomUUID();
        Deposit deposit = new Deposit(fromWallet, userId, new BigDecimal("50.00"), opId, OperationOrigin.SYSTEM);
        Account account = new Account(fromWallet, BigDecimal.ZERO, 1L, userId, Instant.now());

        when(operationsDao.startOperation(opId)).thenReturn(true);
        when(accountDao.findAccount(deposit.walletId())).thenReturn(Optional.of(account));

        depositFundsService.handle(deposit);

        ArgumentCaptor<DepositCompletedEvent> eventCaptor = ArgumentCaptor.forClass(DepositCompletedEvent.class);
        verify(outboxDao).save(eventCaptor.capture());

        DepositCompletedEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.origin()).isEqualTo(OperationOrigin.SYSTEM);
    }
}
