package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.infrastructure.messaging.consumer.DlqConsumer;
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

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqConsumer Unit Tests")
class DlqConsumerTest {

    @Mock
    private Connection connection;

    @Mock
    private DlqManagementUseCase dlqManagementUseCase;

    @Mock
    private Message message;

    private DlqConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DlqConsumer(connection, dlqManagementUseCase);
    }

    @Test
    @DisplayName("Should parse headers, delegate to DlqManagementUseCase.recordDlqEvent, and ACK message")
    void shouldProcessDlqMessageSuccessfully() throws Exception {
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant failedAt = Instant.now();

        Headers headers = new Headers();
        headers.add("operation_id", operationId.toString());
        headers.add("userId", userId.toString());
        headers.add("original_subject", "commands.deposit");
        headers.add("failed_at", failedAt.toString());
        headers.add("error_message", "DB Lock timeout");
        headers.add("failure_type", "TRANSIENT");
        headers.add("type", "Deposit");

        when(message.getHeaders()).thenReturn(headers);
        when(message.getData()).thenReturn("{\"amount\": 100.00}".getBytes());

        Method processMethod = DlqConsumer.class.getDeclaredMethod("processMessage", Message.class);
        processMethod.setAccessible(true);
        processMethod.invoke(consumer, message);

        ArgumentCaptor<DlqEvent> eventCaptor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqManagementUseCase).recordDlqEvent(eventCaptor.capture());

        DlqEvent recorded = eventCaptor.getValue();
        assertThat(recorded.operationId()).isEqualTo(operationId);
        assertThat(recorded.userId()).isEqualTo(userId);
        assertThat(recorded.subject()).isEqualTo("commands.deposit");
        assertThat(recorded.status()).isEqualTo(DlqStatus.PENDING);
        assertThat(recorded.failureType()).isEqualTo(DlqFailureType.TRANSIENT);
        assertThat(recorded.eventType()).isEqualTo("Deposit");

        verify(message).ack();
    }
}
