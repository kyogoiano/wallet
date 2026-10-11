package br.com.wallet.unit.ledger.outbox;

import br.com.wallet.ledger.api.event.DomainEventType;
import br.com.wallet.ledger.api.event.EventPublisher;
import br.com.wallet.ledger.api.utils.JsonUtils;
import br.com.wallet.ledger.internal.outbox.OutboxEvent;
import br.com.wallet.ledger.internal.outbox.OutboxEventProcessor;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventProcessor Unit Tests (REQ-OBS-001, I-OBS-001)")
class OutboxEventProcessorTest {

    @Mock
    private EventPublisher publisher;

    @Mock
    private OutboxDao<OutboxEvent> outboxDao;

    @Mock
    private JsonUtils jsonUtils;

    @InjectMocks
    private OutboxEventProcessor processor;

    @Test
    @DisplayName("Should publish event and mark as processed when valid")
    void shouldPublishAndMarkAsProcessed() throws IOException {
        var now = Instant.now();
        UUID opId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(
                eventId,
                DomainEventType.TRANSFER_COMPLETED,
                "{\"foo\":\"bar\"}",
                0,
                opId,
                "type",
                UUID.randomUUID()
        );

        processor.processEvent(event, now);

        verify(publisher).publish(eq(DomainEventType.TRANSFER_COMPLETED), eq("{\"foo\":\"bar\"}"), eq(opId));
        verify(outboxDao).markAsProcessed(eq(eventId), eq(now));
        verify(outboxDao, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("Should schedule retry with backoff when publisher throws exception")
    void shouldMarkFailedWithBackoffOnPublisherException() throws IOException {
        var now = Instant.now();
        UUID opId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(
                eventId,
                DomainEventType.TRANSFER_COMPLETED,
                "{\"foo\":\"bar\"}",
                0,
                opId,
                "type",
                UUID.randomUUID()
        );

        doThrow(new RuntimeException("NATS unavailable"))
                .when(publisher)
                .publish(any(), any(), any());

        processor.processEvent(event, now);

        var expectedBackoff = Duration.ofSeconds((long) Math.pow(2, 1));
        verify(outboxDao).markFailed(eq(eventId), eq(now.plus(expectedBackoff)));
        verify(outboxDao, never()).markAsProcessed(any(), any());
    }

    @Test
    @DisplayName("Should mark event as dead when retryCount > 10")
    void shouldMarkAsDeadWhenRetriesExhausted() throws IOException {
        var now = Instant.now();
        UUID opId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        var event = new OutboxEvent(
                eventId,
                DomainEventType.TRANSFER_COMPLETED,
                "{\"foo\":\"bar\"}",
                10, // 10 + 1 = 11 > 10
                opId,
                "type",
                UUID.randomUUID()
        );

        doThrow(new RuntimeException("Permanent error"))
                .when(publisher)
                .publish(any(), any(), any());

        processor.processEvent(event, now);

        verify(outboxDao).markAsDead(eq(eventId), eq(now));
        verify(outboxDao, never()).markFailed(any(), any());
    }
}
