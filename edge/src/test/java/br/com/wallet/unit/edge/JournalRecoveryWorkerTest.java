package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.CorruptedJournalException;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.spi.DurableSpilloverJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import br.com.wallet.edge.internal.recovery.JournalRecoveryWorker;
import br.com.wallet.edge.internal.recovery.SpoolAckTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("JournalRecoveryWorker Unit Tests (TASK-4.1, TASK-4.2, REQ-EDG-009, REQ-EDG-014, REQ-EDG-018)")
class JournalRecoveryWorkerTest {

    private DurableSpilloverJournal journal;
    private EdgeCommandPublisher publisher;
    private SpoolAckTracker ackTracker;
    private EdgeReadinessHealthIndicator healthIndicator;
    private JournalRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        journal = mock(DurableSpilloverJournal.class);
        publisher = mock(EdgeCommandPublisher.class);
        ackTracker = mock(SpoolAckTracker.class);
        healthIndicator = new EdgeReadinessHealthIndicator();

        worker = new JournalRecoveryWorker(
                journal,
                publisher,
                ackTracker,
                healthIndicator,
                0.80,
                0.20
        );
    }

    @Test
    @DisplayName("Should scan segments, replay records to NATS with operationId, and transition readiness to READY (I-EDGE-004)")
    void shouldRecoverAndDrainBacklog() throws Exception {
        Path seg1 = Path.of("/spool/segment-0000000000000001.wal");
        when(journal.listSegmentFiles()).thenReturn(List.of(seg1));

        UUID opId1 = UUID.randomUUID();
        UUID opId2 = UUID.randomUUID();
        JournalRecord r1 = new JournalRecord(1L, opId1, System.currentTimeMillis(), CommandType.TRANSFER, "{\"amount\": 100.00}".getBytes());
        JournalRecord r2 = new JournalRecord(2L, opId2, System.currentTimeMillis(), CommandType.DEPOSIT, "{\"amount\": 200.00}".getBytes());

        when(journal.readSegmentRecords(seg1)).thenReturn(List.of(r1, r2));
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(ackTracker.isSegmentFullyAcknowledged(seg1)).thenReturn(true);

        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.INITIALIZING);

        worker.runRecoveryScan();

        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.READY);
        assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("UP");

        ArgumentCaptor<CommandEnvelope> captor = ArgumentCaptor.forClass(CommandEnvelope.class);
        verify(publisher, times(2)).publish(captor.capture());

        List<CommandEnvelope> published = captor.getAllValues();
        assertThat(published.get(0).operationId()).isEqualTo(opId1);
        assertThat(published.get(1).operationId()).isEqualTo(opId2);

        verify(ackTracker).trackSegment(eq(seg1), eq(List.of(1L, 2L)));
        verify(ackTracker).acknowledgeRecord(seg1, 1L);
        verify(ackTracker).acknowledgeRecord(seg1, 2L);
        verify(ackTracker).reclaimIfFullyAcknowledged(seg1);
    }

    @Test
    @DisplayName("Should halt segment scan and quarantine corrupted segment without DLQ pollution on CRC error (REQ-EDG-018)")
    void shouldHaltOnCrcFailureAndQuarantine() throws IOException {
        Path segCorrupt = Path.of("/spool/segment-0000000000000002.wal");
        when(journal.listSegmentFiles()).thenReturn(List.of(segCorrupt));

        when(journal.readSegmentRecords(segCorrupt))
                .thenThrow(new CorruptedJournalException("CRC32C checksum mismatch at offset 1024"));

        worker.runRecoveryScan();

        // Must isolate corrupted segment
        verify(journal).quarantineSegment(eq(segCorrupt), contains("CRC32C checksum mismatch"));
        // Never publish corrupted bytes to NATS or DLQ
        verifyNoInteractions(publisher);
        // Node state remains DEGRADED to alert ops
        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.DEGRADED);
        assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("Should skip records already acknowledged in previous run before restart (TASK-4.5, TASK-4.6)")
    void shouldSkipAlreadyAcknowledgedRecords() throws Exception {
        Path seg1 = Path.of("/spool/segment-0000000000000003.wal");
        when(journal.listSegmentFiles()).thenReturn(List.of(seg1));

        UUID opId1 = UUID.randomUUID();
        UUID opId2 = UUID.randomUUID();
        JournalRecord r1 = new JournalRecord(10L, opId1, System.currentTimeMillis(), CommandType.TRANSFER, "{\"amount\": 10.00}".getBytes());
        JournalRecord r2 = new JournalRecord(20L, opId2, System.currentTimeMillis(), CommandType.DEPOSIT, "{\"amount\": 20.00}".getBytes());

        when(journal.readSegmentRecords(seg1)).thenReturn(List.of(r1, r2));
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(ackTracker.isRecordAcknowledged(seg1, 10L)).thenReturn(true);
        when(ackTracker.isRecordAcknowledged(seg1, 20L)).thenReturn(false);
        when(ackTracker.isSegmentFullyAcknowledged(seg1)).thenReturn(true);

        worker.runRecoveryScan();

        ArgumentCaptor<CommandEnvelope> captor = ArgumentCaptor.forClass(CommandEnvelope.class);
        verify(publisher, times(1)).publish(captor.capture());
        assertThat(captor.getValue().operationId()).isEqualTo(opId2);

        verify(ackTracker, never()).acknowledgeRecord(seg1, 10L);
        verify(ackTracker, times(1)).acknowledgeRecord(seg1, 20L);
        verify(ackTracker).reclaimIfFullyAcknowledged(seg1);
    }
}
