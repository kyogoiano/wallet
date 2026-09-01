package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.fraud.intelligence.projector.RelationalGraphProjector;
import br.com.wallet.infrastructure.messaging.consumer.FraudGraphConsumer;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import io.nats.client.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudGraphEventListener Unit Tests (Architecture & Event Ingestion)")
class FraudGraphConsumerTest {

    @Mock
    private Connection connection;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RelationalGraphProjector projector;

    @Mock
    private AccountUseCase accountUseCase;

    private FraudGraphConsumer listener;

    @BeforeEach
    void setUp() {
        listener = new FraudGraphConsumer(connection, objectMapper, projector, accountUseCase);
    }

    @Test
    @DisplayName("Should project transfer using resolved userIds from AccountUseCase")
    void shouldHandleTransferCompletedEventWithResolvedUserIds() throws Exception {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.00");

        Account sourceAccount = new Account(walletFrom, BigDecimal.valueOf(1000), 1L, userFrom, AccountStatus.ACTIVE, null, null, Instant.now());
        Account targetAccount = new Account(walletTo, BigDecimal.valueOf(500), 1L, userTo, AccountStatus.ACTIVE, null, null, Instant.now());

        when(accountUseCase.find(walletFrom)).thenReturn(sourceAccount);
        when(accountUseCase.find(walletTo)).thenReturn(targetAccount);

        TransferCompletedEvent event = new TransferCompletedEvent(walletFrom, walletTo, amount, operationId);

        Method handleMethod = FraudGraphConsumer.class.getDeclaredMethod("handle", TransferCompletedEvent.class);
        handleMethod.setAccessible(true);
        handleMethod.invoke(listener, event);

        verify(accountUseCase).find(walletFrom);
        verify(accountUseCase).find(walletTo);
        verify(projector).projectTransfer(
            eq(walletFrom),
            eq(walletTo),
            eq(userFrom),
            eq(userTo),
            eq(amount),
            eq(operationId),
            any(Instant.class)
        );
    }

    @Test
    @DisplayName("Should fallback to walletId when AccountUseCase throws exception")
    void shouldHandleTransferCompletedEventWithFallbackWhenAccountNotFound() throws Exception {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        when(accountUseCase.find(walletFrom)).thenThrow(new IllegalArgumentException("Wallet not found"));
        when(accountUseCase.find(walletTo)).thenThrow(new IllegalArgumentException("Wallet not found"));

        TransferCompletedEvent event = new TransferCompletedEvent(walletFrom, walletTo, amount, operationId);

        Method handleMethod = FraudGraphConsumer.class.getDeclaredMethod("handle", TransferCompletedEvent.class);
        handleMethod.setAccessible(true);
        handleMethod.invoke(listener, event);

        verify(projector).projectTransfer(
            eq(walletFrom),
            eq(walletTo),
            eq(walletFrom),
            eq(walletTo),
            eq(amount),
            eq(operationId),
            any(Instant.class)
        );
    }
}
