package br.com.wallet.integration.dlq;

import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.engine.DlqReplayEngine;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("DlqReplayEngine Integration Tests (TASK-3.1, REQ-TDLQ-005, I-TDLQ-001, I-TDLQ-003)")
class DlqReplayEngineIT extends DockerProperties {

    @Autowired
    private DlqReplayEngine replayEngine;

    @Autowired
    private DlqOperationsDao dlqDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @MockitoBean
    private Connection natsConnection;

    @MockitoBean
    private JetStream jetStream;

    @MockitoBean
    private JetStreamManagement jetStreamManagement;

    @BeforeEach
    void setup() throws Exception {
        cleaner.clean();
        when(natsConnection.jetStream()).thenReturn(jetStream);
        when(natsConnection.jetStreamManagement()).thenReturn(jetStreamManagement);
    }

    @Test
    @DisplayName("Should claim pending records via SKIP LOCKED and publish replay message")
    void shouldClaimBatchAndReplay() throws Exception {
        UUID id = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent event = new DlqEvent(
                id, opId, UUID.randomUUID(), "commands.deposit",
                DlqStatus.PENDING, "Transient failure", "{\"amount\":100}",
                0, now.minusSeconds(10), now.minusSeconds(60), null,
                DlqFailureType.TRANSIENT, "Deposit", "tenant-test"
        );
        dlqDao.insert(event);

        replayEngine.process();

        // Verify message was republished to NATS with identity preserved and X-Replay-Id attached
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(messageCaptor.capture());

        Message published = messageCaptor.getValue();
        assertThat(published.getHeaders().getFirst("operation_id")).isEqualTo(opId.toString());
        assertThat(published.getHeaders().getFirst("replay_id")).isNotBlank();
        assertThat(published.getHeaders().getFirst("replayed")).isEqualTo("true");
        assertThat(published.getHeaders().getFirst("tenant_id")).isEqualTo("tenant-test");

        // Verify state transitioned to COMPLETED in database
        DlqEvent completed = dlqDao.findById(id).orElseThrow();
        assertThat(completed.status()).isEqualTo(DlqStatus.COMPLETED);
        assertThat(completed.processedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should ignore QUARANTINED records during automated batch claiming")
    void shouldIgnoreQuarantinedRecords() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent quarantined = new DlqEvent(
                id, UUID.randomUUID(), UUID.randomUUID(), "commands.transfer",
                DlqStatus.QUARANTINED, "Poison message", "{}",
                0, null, now.minusSeconds(60), null,
                DlqFailureType.POISON, "Transfer", "tenant-test"
        );
        dlqDao.insert(quarantined);

        List<DlqEvent> claimed = dlqDao.claimBatch(now, 50);
        assertThat(claimed).isEmpty();
    }
}
