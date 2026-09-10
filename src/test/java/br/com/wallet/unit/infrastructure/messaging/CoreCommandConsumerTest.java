package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.edge.api.OperationStatusBroadcaster;
import br.com.wallet.infrastructure.messaging.consumer.CoreCommandConsumer;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoreCommandConsumer Unit Tests (REQ-EDG-022, I-EDGE-003, History 41)")
class CoreCommandConsumerTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private TransferFundsUseCase transferFundsUseCase;

    @Mock
    private DepositFundsUseCase depositFundsUseCase;

    @Mock
    private WithdrawFundsUseCase withdrawFundsUseCase;

    @Mock
    private FraudCheckHelper fraudCheckHelper;

    @Mock
    private OperationStatusBroadcaster statusBroadcaster;

    @Mock
    private DlqPublisher dlqPublisher;

    @Mock
    private OperationStateUseCase operationStateUseCase;

    @Mock
    private Message message;

    @Mock
    private NatsJetStreamMetaData metaData;

    @Mock
    private PublishAck publishAck;

    private ObjectMapper objectMapper;
    private CoreCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new CoreCommandConsumer(
                natsConnection,
                objectMapper,
                transferFundsUseCase,
                depositFundsUseCase,
                withdrawFundsUseCase,
                fraudCheckHelper,
                statusBroadcaster,
                dlqPublisher,
                operationStateUseCase
        );
    }

    @Test
    @DisplayName("Should process Transfer command, invoke use case, publish COMPLETED, and ACK message")
    void shouldHandleTransferSuccessfully() {
        UUID opId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        String json = """
                {"from":"%s","to":"%s","amount":100.00,"operationId":"%s"}
                """.formatted(from, to, opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);

        consumer.processMessage(message);

        verify(fraudCheckHelper).performFraudCheck(any(Transfer.class));
        verify(transferFundsUseCase).handle(any(Transfer.class));
        verify(statusBroadcaster).publishStatus(eq(opId), eq("COMPLETED"), anyString());
        verify(operationStateUseCase).markOperationCompleted(opId);
        verify(message).ack();
        verify(message, never()).nakWithDelay(any());
    }

    @Test
    @DisplayName("Should process Deposit command, invoke use case, publish COMPLETED, and ACK message")
    void shouldHandleDepositSuccessfully() {
        UUID opId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String json = """
                {"walletId":"%s","amount":50.00,"operationId":"%s"}
                """.formatted(walletId, opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "DEPOSIT");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);

        consumer.processMessage(message);

        verify(fraudCheckHelper).performFraudCheck(any(Deposit.class));
        verify(depositFundsUseCase).handle(any(Deposit.class));
        verify(statusBroadcaster).publishStatus(eq(opId), eq("COMPLETED"), anyString());
        verify(operationStateUseCase).markOperationCompleted(opId);
        verify(message).ack();
    }

    @Test
    @DisplayName("Should process Withdraw command, invoke use case, publish COMPLETED, and ACK message")
    void shouldHandleWithdrawSuccessfully() {
        UUID opId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        String json = """
                {"walletId":"%s","amount":30.00,"operationId":"%s"}
                """.formatted(walletId, opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "WITHDRAW");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);

        consumer.processMessage(message);

        verify(fraudCheckHelper).performFraudCheck(any(Withdraw.class));
        verify(withdrawFundsUseCase).handle(any(Withdraw.class));
        verify(statusBroadcaster).publishStatus(eq(opId), eq("COMPLETED"), anyString());
        verify(operationStateUseCase).markOperationCompleted(opId);
        verify(message).ack();
    }

    @Test
    @DisplayName("Should ACK business rejection (InsufficientFundsException) without retrying and publish FAILED")
    void shouldAckBusinessRejection() {
        UUID opId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        String json = """
                {"from":"%s","to":"%s","amount":1000.00,"operationId":"%s"}
                """.formatted(from, to, opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);

        doThrow(new InsufficientFundsException("Insufficient balance"))
                .when(transferFundsUseCase).handle(any(Transfer.class));

        consumer.processMessage(message);

        verify(statusBroadcaster).publishStatus(eq(opId), eq("FAILED"), contains("Insufficient balance"));
        verify(operationStateUseCase).markOperationFailed(eq(opId), contains("Insufficient balance"), any());
        verify(message).ack();
        verify(message, never()).nakWithDelay(any());
    }

    @Test
    @DisplayName("Should ACK account blocked business rejection without retrying")
    void shouldAckAccountBlockedRejection() {
        UUID opId = UUID.randomUUID();
        String json = """
                {"from":"%s","to":"%s","amount":50.00,"operationId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(1L);

        doThrow(new AccountBlockedException("Source account is BLOCKED"))
                .when(transferFundsUseCase).handle(any(Transfer.class));

        consumer.processMessage(message);

        verify(statusBroadcaster).publishStatus(eq(opId), eq("FAILED"), contains("BLOCKED"));
        verify(message).ack();
        verify(message, never()).nakWithDelay(any());
    }

    @Test
    @DisplayName("Should trigger NAK with backoff on transient failure and notify PROCESSING")
    void shouldRetryTransientFailure() {
        UUID opId = UUID.randomUUID();
        String json = """
                {"from":"%s","to":"%s","amount":20.00,"operationId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(2L);

        doThrow(new RuntimeException(new SQLException("Deadlock detected in postgres")))
                .when(transferFundsUseCase).handle(any(Transfer.class));

        consumer.processMessage(message);

        verify(statusBroadcaster).publishStatus(eq(opId), eq("PROCESSING"), argThat(msg -> msg != null && msg.toLowerCase().contains("retrying")));
        ArgumentCaptor<Duration> delayCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(message).nakWithDelay(delayCaptor.capture());
        assertThat(delayCaptor.getValue()).isGreaterThan(Duration.ZERO);
        verify(message, never()).ack();
    }

    @Test
    @DisplayName("Should publish poison message to DLQ, await confirmed PubAck, and ONLY THEN ACK original message (History 41)")
    void shouldRoutePoisonMessageToDlq() throws Exception {
        byte[] unparseableBytes = "{ corrupt json payload".getBytes(StandardCharsets.UTF_8);

        Headers headers = new Headers();
        headers.add("operation_id", UUID.randomUUID().toString());

        when(message.getSubject()).thenReturn("commands.wallet.transfer");
        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(unparseableBytes);
        when(dlqPublisher.publishDlqConfirmed(anyString(), any(), eq(message), any())).thenReturn(publishAck);

        consumer.processMessage(message);

        InOrder inOrder = inOrder(dlqPublisher, message);
        inOrder.verify(dlqPublisher).publishDlqConfirmed(eq("commands.dlq.transfer"), eq(natsConnection), eq(message), any());
        inOrder.verify(message).ack();
        verify(message, never()).nakWithDelay(any());
    }

    @Test
    @DisplayName("Should route to DLQ when delivery count reaches maximum retries threshold")
    void shouldRouteToDlqOnMaxRetries() throws Exception {
        UUID opId = UUID.randomUUID();
        String json = """
                {"from":"%s","to":"%s","amount":10.00,"operationId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), opId);

        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        when(message.metaData()).thenReturn(metaData);
        when(metaData.deliveredCount()).thenReturn(5L); // Delivery count >= 5

        doThrow(new RuntimeException("Persistent database connection error"))
                .when(transferFundsUseCase).handle(any(Transfer.class));
        when(dlqPublisher.publishDlqConfirmed(anyString(), any(), eq(message), any())).thenReturn(publishAck);

        consumer.processMessage(message);

        verify(dlqPublisher).publishDlqConfirmed(eq("commands.dlq.transfer"), eq(natsConnection), eq(message), any());
        verify(statusBroadcaster).publishStatus(eq(opId), eq("FAILED"), contains("DLQ"));
        verify(message).ack();
    }
}
