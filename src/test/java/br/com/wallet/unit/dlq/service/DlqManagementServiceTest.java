package br.com.wallet.unit.dlq.service;

import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.dlq.internal.service.DlqManagementService;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqManagementService Unit Tests")
class DlqManagementServiceTest {

    @Mock
    private DlqOperationsDao dlqDao;

    @Mock
    private Connection connection;

    @Mock
    private JetStream jetStream;

    private Clock clock;
    private DlqManagementService managementService;
    private final Instant now = Instant.parse("2026-08-28T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        managementService = new DlqManagementService(dlqDao, connection, clock);
    }

    @Test
    @DisplayName("Should record DLQ event into DAO")
    void shouldRecordDlqEvent() {
        DlqEvent event = new DlqEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.PENDING, "error", "{}", 0, null, now, null,
                DlqFailureType.TRANSIENT, "Deposit"
        );

        managementService.recordDlqEvent(event);

        verify(dlqDao).insert(eq(event));
    }

    @Test
    @DisplayName("Should manually replay an EXHAUSTED operation and mark it completed")
    void shouldManuallyReplayExhaustedOperation() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DlqEvent event = new DlqEvent(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.EXHAUSTED, "DB lock timeout", "{\"amount\": 100}",
                3, null, now.minusSeconds(300), null,
                DlqFailureType.TRANSIENT, "Deposit"
        );
        DlqEvent completedEvent = new DlqEvent(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.COMPLETED, "DB lock timeout", "{\"amount\": 100}",
                3, null, now.minusSeconds(300), now,
                DlqFailureType.TRANSIENT, "Deposit"
        );

        when(dlqDao.findById(eventId)).thenReturn(Optional.of(event), Optional.of(completedEvent));
        when(connection.jetStream()).thenReturn(jetStream);

        DlqOperationResponse response = managementService.replayOperation(eventId);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("commands.deposit");

        verify(dlqDao).markAsCompleted(eq(eventId), eq(now));
        assertThat(response.status()).isEqualTo(DlqStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should reject manual replay of already COMPLETED operation")
    void shouldRejectReplayOfCompletedOperation() {
        UUID eventId = UUID.randomUUID();
        DlqEvent completedEvent = new DlqEvent(
                eventId, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.COMPLETED, null, "{}",
                1, null, now.minusSeconds(300), now,
                DlqFailureType.TRANSIENT, "Deposit"
        );

        when(dlqDao.findById(eventId)).thenReturn(Optional.of(completedEvent));

        assertThatThrownBy(() -> managementService.replayOperation(eventId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot replay already COMPLETED");
    }

    @Test
    @DisplayName("Should manually discard an operation")
    void shouldManuallyDiscardOperation() {
        UUID eventId = UUID.randomUUID();
        DlqEvent event = new DlqEvent(
                eventId, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.EXHAUSTED, "Poison payload", "{}",
                3, null, now.minusSeconds(300), null,
                DlqFailureType.POISON, "Deposit"
        );
        DlqEvent discardedEvent = new DlqEvent(
                eventId, event.operationId(), event.userId(), "commands.deposit",
                DlqStatus.DISCARDED, "Operator confirmed unrecoverable", "{}",
                3, null, now.minusSeconds(300), now,
                DlqFailureType.POISON, "Deposit"
        );

        when(dlqDao.findById(eventId)).thenReturn(Optional.of(event), Optional.of(discardedEvent));

        DlqOperationResponse response = managementService.discardOperation(eventId, "Operator confirmed unrecoverable");

        verify(dlqDao).markAsDiscarded(eq(eventId), eq(now), eq("Operator confirmed unrecoverable"));
        assertThat(response.status()).isEqualTo(DlqStatus.DISCARDED);
    }

    @Test
    @DisplayName("Should batch replay all exhausted operations")
    void shouldBatchReplayAllExhausted() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        DlqEvent ev1 = new DlqEvent(id1, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit", DlqStatus.EXHAUSTED, "err", "{}", 3, null, now, null, DlqFailureType.TRANSIENT, "Deposit");
        DlqEvent ev2 = new DlqEvent(id2, UUID.randomUUID(), UUID.randomUUID(), "commands.transfer", DlqStatus.EXHAUSTED, "err", "{}", 3, null, now, null, DlqFailureType.TRANSIENT, "Transfer");

        when(dlqDao.findExhaustedOperations(100)).thenReturn(List.of(ev1, ev2));
        when(connection.jetStream()).thenReturn(jetStream);

        ReplayExhaustedResult result = managementService.replayAllExhausted();

        assertThat(result.replayedCount()).isEqualTo(2);
        assertThat(result.operationIds()).containsExactly(id1, id2);
        verify(dlqDao).markAsCompleted(eq(id1), eq(now));
        verify(dlqDao).markAsCompleted(eq(id2), eq(now));
    }
}
