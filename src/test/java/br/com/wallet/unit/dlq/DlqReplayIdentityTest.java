package br.com.wallet.unit.dlq;

import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.engine.DlqReplayEngine;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqReplayIdentityTest (TASK-3.3, REQ-TDLQ-006, I-TDLQ-005)")
class DlqReplayIdentityTest {

    @Mock
    private DlqOperationsDao dlqDao;

    @Mock
    private Connection connection;

    @Mock
    private JetStream jetStream;

    private DlqReplayEngine replayEngine;
    private final Instant now = Instant.parse("2026-10-03T15:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        replayEngine = new DlqReplayEngine(clock, dlqDao, connection);
        when(connection.jetStream()).thenReturn(jetStream);
    }

    @Test
    @DisplayName("Should preserve operation_id and attach unique X-Replay-Id without client nonce (I-TDLQ-005)")
    void shouldPreserveOperationIdAndAttachReplayId() throws Exception {
        UUID opId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        DlqEvent event1 = new DlqEvent(
                eventId1, opId, UUID.randomUUID(), "commands.transfer",
                DlqStatus.PENDING, "Transient deadlock", "{}",
                0, null, now.minusSeconds(30), null,
                DlqFailureType.TRANSIENT, "Transfer", "tenant-finance"
        );
        DlqEvent event2 = new DlqEvent(
                eventId2, opId, UUID.randomUUID(), "commands.transfer",
                DlqStatus.PENDING, "Transient deadlock", "{}",
                1, null, now.minusSeconds(10), null,
                DlqFailureType.TRANSIENT, "Transfer", "tenant-finance"
        );

        when(dlqDao.claimBatch(eq(now), eq(50))).thenReturn(List.of(event1, event2));

        replayEngine.process();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream, times(2)).publish(messageCaptor.capture());

        List<Message> publishedMessages = messageCaptor.getAllValues();
        Message msg1 = publishedMessages.get(0);
        Message msg2 = publishedMessages.get(1);

        // 1. Assert financial identity immutability (same original operation_id preserved)
        assertThat(msg1.getHeaders().getFirst("operation_id")).isEqualTo(opId.toString());
        assertThat(msg2.getHeaders().getFirst("operation_id")).isEqualTo(opId.toString());

        // 2. Assert canonical replayed flag and absence of legacy X-Replayed
        assertThat(msg1.getHeaders().getFirst("replayed")).isEqualTo("true");
        assertThat(msg1.getHeaders().getFirst("X-Replayed")).isNull();
        assertThat(msg2.getHeaders().getFirst("replayed")).isEqualTo("true");
        assertThat(msg2.getHeaders().getFirst("X-Replayed")).isNull();

        // 3. Assert unique operational replay_id generated for each replay attempt
        String replayId1 = msg1.getHeaders().getFirst("replay_id");
        String replayId2 = msg2.getHeaders().getFirst("replay_id");
        assertThat(replayId1).isNotBlank();
        assertThat(replayId2).isNotBlank();
        assertThat(replayId1).isNotEqualTo(replayId2);
        assertThat(msg1.getHeaders().getFirst("X-Replay-Id")).isNull();
        assertThat(msg2.getHeaders().getFirst("X-Replay-Id")).isNull();

        // 4. Assert client nonce header is NOT reused
        assertThat(msg1.getHeaders().getFirst("X-Nonce")).isNull();
        assertThat(msg2.getHeaders().getFirst("X-Nonce")).isNull();

        // 5. Assert canonical tenant_id propagation and absence of legacy X-Tenant-Id
        assertThat(msg1.getHeaders().getFirst("tenant_id")).isEqualTo("tenant-finance");
        assertThat(msg1.getHeaders().getFirst("X-Tenant-Id")).isNull();
        assertThat(msg2.getHeaders().getFirst("tenant_id")).isEqualTo("tenant-finance");
        assertThat(msg2.getHeaders().getFirst("X-Tenant-Id")).isNull();
    }
}
