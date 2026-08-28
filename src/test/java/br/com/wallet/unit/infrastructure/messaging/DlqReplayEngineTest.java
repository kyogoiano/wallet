package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.engine.DlqReplayEngine;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.ledger.api.exceptions.TransientException;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqReplayEngine Unit Tests")
class DlqReplayEngineTest {

    @Mock
    private DlqOperationsDao dlqDao;

    @Mock
    private Connection connection;

    @Mock
    private JetStream jetStream;

    private Clock clock;
    private DlqReplayEngine replayEngine;
    private final Instant now = Instant.parse("2026-08-28T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        replayEngine = new DlqReplayEngine(clock, dlqDao, connection);
    }

    @Test
    @DisplayName("Should successfully replay DLQ event to JetStream and mark completed")
    void shouldReplayDlqEventSuccessfully() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DlqEvent event = new DlqEvent(
                eventId,
                operationId,
                userId,
                "commands.deposit",
                DlqStatus.PENDING,
                "DB lock timeout",
                "{\"amount\": 100.00}",
                0,
                null,
                now.minusSeconds(60),
                null,
                DlqFailureType.TRANSIENT,
                "Deposit"
        );

        when(dlqDao.claimBatch(eq(now), eq(50))).thenReturn(List.of(event));
        when(connection.jetStream()).thenReturn(jetStream);

        replayEngine.process();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(messageCaptor.capture());

        Message published = messageCaptor.getValue();
        assertThat(published.getSubject()).isEqualTo("commands.deposit");
        assertThat(published.getHeaders().getFirst("operation_id")).isEqualTo(operationId.toString());
        assertThat(published.getHeaders().getFirst("userId")).isEqualTo(userId.toString());
        assertThat(published.getHeaders().getFirst("replayed")).isEqualTo("true");
        assertThat(published.getHeaders().getFirst("type")).isEqualTo("Deposit");

        verify(dlqDao).markAsCompleted(eq(eventId), eq(now));
    }

    @Test
    @DisplayName("Should handle transient failure during replay and mark TRANSIENT")
    void shouldHandleTransientFailureDuringReplay() throws Exception {
        UUID eventId = UUID.randomUUID();
        DlqEvent event = new DlqEvent(
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "commands.deposit",
                DlqStatus.PENDING,
                "DB lock timeout",
                "{\"amount\": 100.00}",
                0,
                null,
                now.minusSeconds(60),
                null,
                DlqFailureType.TRANSIENT,
                "Deposit"
        );

        when(dlqDao.claimBatch(eq(now), eq(50))).thenReturn(List.of(event));
        when(connection.jetStream()).thenThrow(new TransientException("NATS temporarily unavailable"));

        replayEngine.process();

        verify(dlqDao).markFailed(eq(eventId), eq(now), eq(DlqFailureType.TRANSIENT));
        verify(dlqDao, never()).markAsCompleted(any(), any());
    }

    @Test
    @DisplayName("Should handle unexpected error during replay and mark POISON")
    void shouldHandleUnexpectedErrorDuringReplay() throws Exception {
        UUID eventId = UUID.randomUUID();
        DlqEvent event = new DlqEvent(
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "commands.deposit",
                DlqStatus.PENDING,
                "Error",
                "malformed payload",
                0,
                null,
                now.minusSeconds(60),
                null,
                DlqFailureType.POISON,
                "Deposit"
        );

        when(dlqDao.claimBatch(eq(now), eq(50))).thenReturn(List.of(event));
        when(connection.jetStream()).thenThrow(new RuntimeException("Fatal error"));

        replayEngine.process();

        verify(dlqDao).markFailed(eq(eventId), eq(now), eq(DlqFailureType.POISON));
    }
}
