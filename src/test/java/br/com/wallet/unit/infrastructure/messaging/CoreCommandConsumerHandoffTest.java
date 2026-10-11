package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.dto.DlqCommandFailure;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.infrastructure.messaging.consumer.CoreCommandConsumer;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoreCommandConsumer Handoff Tests (TASK-4.1, TASK-4.2, I-TDLQ-001, I-TDLQ-002, I-TDLQ-009)")
class CoreCommandConsumerHandoffTest {

    @Mock private Connection natsConnection;
    @Mock private TransferFundsUseCase transferFundsUseCase;
    @Mock private DepositFundsUseCase depositFundsUseCase;
    @Mock private WithdrawFundsUseCase withdrawFundsUseCase;
    @Mock private FraudCheckHelper fraudCheckHelper;
    @Mock private DlqPublisher dlqPublisher;
    @Mock private OperationStateUseCase operationStateUseCase;
    @Mock private DlqManagementUseCase dlqManagementUseCase;
    @Mock private Message failedMessage;

    private CoreCommandConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new CoreCommandConsumer(
                natsConnection,
                objectMapper,
                transferFundsUseCase,
                depositFundsUseCase,
                withdrawFundsUseCase,
                fraudCheckHelper,
                null, // Zero coupling to Edge OperationStatusBroadcaster
                dlqPublisher,
                operationStateUseCase,
                null,
                null,
                dlqManagementUseCase
        );
    }

    @Test
    @DisplayName("Should commit failure to DLQ storage and ACK NATS to unblock live ingress (I-TDLQ-001, I-TDLQ-002, I-TDLQ-009)")
    void shouldCommitToDlqAndAckNats() {
        UUID opId = UUID.randomUUID();
        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("tenant_id", "tenant-retail");
        headers.add("publisher_id", "edge-gateway");
        headers.add("principal_id", "edge-worker-1");
        headers.add("key_id", "key-1");

        when(failedMessage.getHeaders()).thenReturn(headers);
        when(failedMessage.getSubject()).thenReturn("commands.transfer");
        // Malformed JSON triggering deserialization poison error
        when(failedMessage.getData()).thenReturn("{not-valid-json".getBytes());

        // Invoke consumer onMessage
        consumer.processMessage(failedMessage);

        // 1. Verify durable handoff to DLQ was committed
        ArgumentCaptor<DlqCommandFailure> failureCaptor = ArgumentCaptor.forClass(DlqCommandFailure.class);
        verify(dlqManagementUseCase).recordFailure(failureCaptor.capture());

        DlqCommandFailure recorded = failureCaptor.getValue();
        assertThat(recorded.operationId()).isEqualTo(opId);
        assertThat(recorded.failureType()).isEqualTo(DlqFailureType.POISON);
        assertThat(recorded.tenantId()).isEqualTo("tenant-retail");

        // 2. Verify NATS message was ACKed after durable commit
        verify(failedMessage).ack();
        verify(failedMessage, never()).nakWithDelay(any());
    }

    @Test
    @DisplayName("Should NOT ACK NATS if DLQ database persistence fails (I-TDLQ-009)")
    void shouldNotAckNatsIfDbFails() {
        UUID opId = UUID.randomUUID();
        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");
        headers.add("tenant_id", "tenant-retail");
        headers.add("publisher_id", "edge-gateway");
        headers.add("principal_id", "edge-worker-1");
        headers.add("key_id", "key-1");

        when(failedMessage.getHeaders()).thenReturn(headers);
        when(failedMessage.getSubject()).thenReturn("commands.transfer");
        when(failedMessage.getData()).thenReturn("{bad-json".getBytes());

        // Simulate database outage during DLQ handoff
        doThrow(new RuntimeException("PostgreSQL connection timeout"))
                .when(dlqManagementUseCase).recordFailure(any());

        consumer.processMessage(failedMessage);

        // Assert message is NOT ACKed
        verify(failedMessage, never()).ack();
        // Assert message is NAKed with delay for redelivery
        verify(failedMessage).nakWithDelay(Duration.ofSeconds(5));
    }
}
