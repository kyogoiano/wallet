package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.edge.api.OperationStatusBroadcaster;
import br.com.wallet.infrastructure.messaging.consumer.CoreCommandConsumer;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CoreCommandConsumerAckTest: Graceful Shutdown & Financial ACK Semantics (REQ-PRC-022 & I-GRACEFUL-001)")
class CoreCommandConsumerAckTest {

    @Mock private Connection connection;
    @Mock private TransferFundsUseCase transferFundsUseCase;
    @Mock private DepositFundsUseCase depositFundsUseCase;
    @Mock private WithdrawFundsUseCase withdrawFundsUseCase;
    @Mock private FraudCheckHelper fraudCheckHelper;
    @Mock private OperationStatusBroadcaster statusBroadcaster;
    @Mock private DlqPublisher dlqPublisher;
    @Mock private OperationStateUseCase operationStateUseCase;
    @Mock private Message message;

    private ObjectMapper objectMapper;
    private CoreCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        consumer = new CoreCommandConsumer(
                connection,
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
    @DisplayName("REQ-PRC-022: Should ACK message ONLY after financial use case handle() commits successfully")
    void shouldAckOnlyAfterFinancialEffect() {
        UUID opId = UUID.randomUUID();
        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        String payload = """
                {"operationId":"%s","from":"%s","to":"%s","amount":100.00}
                """.formatted(opId, UUID.randomUUID(), UUID.randomUUID());

        when(message.getHeaders()).thenReturn(headers);
        when(message.getSubject()).thenReturn("commands.wallet.transfer");
        when(message.getData()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));

        consumer.processMessage(message);

        verify(transferFundsUseCase, times(1)).handle(any(Transfer.class));
        verify(statusBroadcaster, times(1)).publishStatus(eq(opId), eq("COMPLETED"), anyString());
        verify(message, times(1)).ack();
    }

    @Test
    @DisplayName("REQ-PRC-022: Should NEVER ACK unprocessed command if failure occurs before durability commit")
    void shouldNotAckUnprocessedCommandOnFatalCrash() {
        UUID opId = UUID.randomUUID();
        Headers headers = new Headers();
        headers.add("operation_id", opId.toString());
        headers.add("type", "TRANSFER");

        String payload = """
                {"operationId":"%s","from":"%s","to":"%s","amount":100.00}
                """.formatted(opId, UUID.randomUUID(), UUID.randomUUID());

        when(message.getHeaders()).thenReturn(headers);
        when(message.getSubject()).thenReturn("commands.wallet.transfer");
        when(message.getData()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));

        // Simulate crash/transient failure in domain execution
        doThrow(new RuntimeException("Transient DB timeout / SIGTERM during transaction"))
                .when(transferFundsUseCase).handle(any(Transfer.class));

        consumer.processMessage(message);

        // Crucial invariant: message MUST NOT be acknowledged with normal ack()
        verify(message, never()).ack();
        // Instead, message was NAKed with retry delay or routed according to retry policy
        verify(message, atLeastOnce()).nakWithDelay(any());
    }
}
