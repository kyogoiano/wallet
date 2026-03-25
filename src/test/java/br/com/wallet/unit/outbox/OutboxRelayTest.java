package br.com.wallet.unit.outbox;

import br.com.wallet.infrasctructure.messaging.EventPublisher;
import br.com.wallet.infrasctructure.outbox.OutboxEvent;
import br.com.wallet.infrasctructure.outbox.OutboxRelay;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
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
    void shouldPublishAndMarkAsProcessed() {

        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event = new OutboxEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                "{\"foo\":\"bar\"}"
        );

        when(outboxDao.getOutboxEvents(now))
                .thenReturn(List.of(event));

        relay.process();

        verify(publisher).publish(eq("TRANSFER_COMPLETED"), anyString());
        verify(outboxDao).markAsProcessed(event.id(), now);
        verify(outboxDao, never()).markFailed(any(), any());
    }

    @Test
    void shouldMarkAsFailedWhenPublisherThrows() {

        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event = new OutboxEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                "{}"
        );

        when(outboxDao.getOutboxEvents(now))
                .thenReturn(List.of(event));

        doThrow(new RuntimeException("boom"))
                .when(publisher)
                .publish(any(), any());

        relay.process();

        verify(outboxDao).markFailed(event.id(), now);
        verify(outboxDao, never()).markAsProcessed(any(), any());
    }

    @Test
    void shouldContinueProcessingOtherEventsWhenOneFails() {

        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event1 = new OutboxEvent(
                UUID.randomUUID(),
                "TRANSFER_COMPLETED",
                validTransferPayload()
        );

        var event2 = new OutboxEvent(
                UUID.randomUUID(),
                "DEPOSIT_COMPLETED",
                validDepositPayload()
        );

        when(outboxDao.getOutboxEvents(now))
                .thenReturn(List.of(event1, event2));

        doThrow(new RuntimeException())
                .when(publisher)
                .publish(eq("TRANSFER_COMPLETED"), any());

        relay.process();

        var inOrder = inOrder(outboxDao);

        inOrder.verify(outboxDao).markFailed(event1.id(), now);
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
