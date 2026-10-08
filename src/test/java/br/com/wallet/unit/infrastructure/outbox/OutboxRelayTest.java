package br.com.wallet.unit.infrastructure.outbox;

import br.com.wallet.domain.event.DomainEventType;
import br.com.wallet.infrasctructure.messaging.publisher.EventPublisher;
import br.com.wallet.infrasctructure.outbox.OutboxEvent;
import br.com.wallet.infrasctructure.outbox.OutboxRelay;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private EventPublisher publisher;

    @Mock
    private OutboxDao outboxDao;

    @Mock
    private Clock clock;

    @Mock
    private JsonUtils jsonUtils;

    @InjectMocks
    private OutboxRelay relay;

    @Test
    void shouldPublishAndMarkAsProcessed() throws IOException {

        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event = new OutboxEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                "{\"foo\":\"bar\"}",
                0
        );

        when(outboxDao.claimBatch(now, 100))
                .thenReturn(List.of(event));

        relay.process();

        verify(publisher).publish(eq(DomainEventType.TRANSFER_COMPLETED), anyString());
        verify(outboxDao).markAsProcessed(event.id(), now);
        verify(outboxDao, never()).markFailed(any(), any());
    }

    @Test
    void shouldMarkAsFailedWhenPublisherThrows() throws IOException {

        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event = new OutboxEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                "{}",
                0
        );

        when(outboxDao.claimBatch(now, 100))
                .thenReturn(List.of(event));

        doThrow(new IOException("boom"))
                .when(publisher)
                .publish(any(), any());

        relay.process();

        verify(outboxDao).markFailed(any(), any());
        verify(outboxDao, never()).markAsProcessed(any(), any());
    }

    @Test
    void shouldContinueProcessingOtherEventsWhenOneFails() throws IOException {

        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event1 = new OutboxEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                validTransferPayload(),
                0
        );

        var event2 = new OutboxEvent(
                UUID.randomUUID(),
                "DEPOSIT_COMPLETED",
                validDepositPayload(),
                0
        );

        when(outboxDao.claimBatch(now, 100))
                .thenReturn(List.of(event1, event2));

        doThrow(new RuntimeException())
                .when(publisher)
                .publish(eq(DomainEventType.TRANSFER_COMPLETED), any());

        relay.process();

        var inOrder = inOrder(outboxDao);
        var backoff = Duration.ofSeconds((long) Math.pow(2, 1));
        var realRetryTime = now.plus(backoff);
        inOrder.verify(outboxDao).markFailed(event1.id(), realRetryTime);
        inOrder.verify(outboxDao).markAsProcessed(event2.id(), now);
    }

    private String validTransferPayload() {
        return """
        {
          "from": "%s",
          "to": "%s",
          "amount": "50.00",
          "operationId": "%s"
        }
    """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private String validDepositPayload() {
        return """
        {
          "walletId": "%s",
          "amount": "100.00",
          "operationId": "%s"
        }
    """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }
}
