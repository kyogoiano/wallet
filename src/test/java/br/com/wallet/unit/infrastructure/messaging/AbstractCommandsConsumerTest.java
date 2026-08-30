package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.infrastructure.messaging.consumer.AbstractCommandsConsumer;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.UseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractCommandsConsumerTest {

    @Mock
    Connection natsConnection;

    @Mock
    UseCase<Transfer> useCase;

    @Mock
    DlqPublisher dlqPublisher;

    @Mock
    OperationStateUseCase operationStateUseCase;

    @Mock
    Message message;

    @Mock
    NatsJetStreamMetaData metaData;

    ObjectMapper objectMapper = new ObjectMapper();

    TestTransferConsumer consumer;

    static class TestTransferConsumer extends AbstractCommandsConsumer<Transfer> {
        public TestTransferConsumer(Connection natsConnection, ObjectMapper objectMapper, UseCase<Transfer> useCase, DlqPublisher dlqPublisher, OperationStateUseCase operationStateUseCase) {
            super("commands.transfer", "commands.dlq.transfer", natsConnection, objectMapper, useCase, dlqPublisher, operationStateUseCase, Transfer.class);
        }

        @Override
        public void init() {}

        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }

    @BeforeEach
    void setUp() {
        consumer = new TestTransferConsumer(natsConnection, objectMapper, useCase, dlqPublisher, operationStateUseCase);
    }

    @Test
    void shouldAckAndNotMarkFailedOnSuccessfulProcessing() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER);
        byte[] payload = objectMapper.writeValueAsBytes(transfer);

        when(message.getData()).thenReturn(payload);
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);

        consumer.handleMessage(message);

        verify(useCase).handle(any());
        verify(message).ack();
        verifyNoInteractions(operationStateUseCase);
    }

    @Test
    void shouldMarkOperationFailedAndAckOnInsufficientFundsException() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER);
        byte[] payload = objectMapper.writeValueAsBytes(transfer);

        when(message.getData()).thenReturn(payload);
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);
        doThrow(new InsufficientFundsException("Insufficient funds")).when(useCase).handle(any());

        consumer.handleMessage(message);

        verify(operationStateUseCase).markOperationFailed(eq(opId), eq("Insufficient funds"), eq("BUSINESS"));
        verify(message).ack();
        verifyNoInteractions(dlqPublisher);
    }

    @Test
    void shouldMarkOperationFailedAndAckOnAccountBlockedException() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER);
        byte[] payload = objectMapper.writeValueAsBytes(transfer);

        when(message.getData()).thenReturn(payload);
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);
        doThrow(new AccountBlockedException("Account is blocked")).when(useCase).handle(any());

        consumer.handleMessage(message);

        verify(operationStateUseCase).markOperationFailed(eq(opId), eq("Account is blocked"), eq("BUSINESS"));
        verify(message).ack();
        verifyNoInteractions(dlqPublisher);
    }

    @Test
    void shouldRetryOnTransientExceptionWhenUnderMaxDeliveries() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER);
        byte[] payload = objectMapper.writeValueAsBytes(transfer);

        when(message.getData()).thenReturn(payload);
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);
        doThrow(new RuntimeException("Connection timeout")).when(useCase).handle(any());

        consumer.handleMessage(message);

        verify(message).nakWithDelay(any(Duration.class));
        verifyNoInteractions(operationStateUseCase);
        verifyNoInteractions(dlqPublisher);
    }

    @Test
    void shouldMarkFailedAndSendToDlqWhenRetriesExhausted() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER);
        byte[] payload = objectMapper.writeValueAsBytes(transfer);

        when(message.getData()).thenReturn(payload);
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(5L);
        doThrow(new RuntimeException("Connection timeout")).when(useCase).handle(any());

        consumer.handleMessage(message);

        verify(operationStateUseCase).markOperationFailed(eq(opId), eq("Connection timeout"), eq("TRANSIENT"));
        verify(dlqPublisher).handleDlqMessage(eq("commands.dlq.transfer"), eq(natsConnection), eq(message), any());
        verify(message).ack();
    }
}
