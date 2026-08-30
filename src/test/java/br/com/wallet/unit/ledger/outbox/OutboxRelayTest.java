package br.com.wallet.unit.ledger.outbox;

import br.com.wallet.ledger.api.event.DomainEventType;
import br.com.wallet.ledger.internal.outbox.OutboxEvent;
import br.com.wallet.ledger.internal.outbox.OutboxEventProcessor;
import br.com.wallet.ledger.internal.outbox.OutboxRelay;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay Unit Tests")
class OutboxRelayTest {

    @Mock
    private OutboxDao<OutboxEvent> outboxDao;

    @Mock
    private Clock clock;

    @Mock
    private OutboxEventProcessor eventProcessor;

    @InjectMocks
    private OutboxRelay relay;

    @Test
    @DisplayName("Should claim batch and delegate each event to OutboxEventProcessor")
    void shouldClaimBatchAndDelegateToEventProcessor() {
        var now = Instant.now();
        when(clock.instant()).thenReturn(now);

        var event1 = new OutboxEvent(
                UUID.randomUUID(),
                DomainEventType.TRANSFER_COMPLETED,
                "{\"foo\":\"bar\"}",
                0,
                UUID.randomUUID(),
                "type",
                UUID.randomUUID()
        );
        var event2 = new OutboxEvent(
                UUID.randomUUID(),
                DomainEventType.DEPOSIT_COMPLETED,
                "{\"foo\":\"baz\"}",
                0,
                UUID.randomUUID(),
                "type",
                UUID.randomUUID()
        );

        when(outboxDao.claimBatch(now, 100))
                .thenReturn(List.of(event1, event2));

        relay.process();

        verify(eventProcessor).processEvent(event1, now);
        verify(eventProcessor).processEvent(event2, now);
    }
}
