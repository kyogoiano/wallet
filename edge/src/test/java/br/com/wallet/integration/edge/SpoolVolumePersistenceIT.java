package br.com.wallet.integration.edge;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import br.com.wallet.edge.internal.recovery.JournalRecoveryWorker;
import br.com.wallet.edge.internal.recovery.SpoolAckTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Spool Volume Persistence & Recovery Integration Test (I-STORAGE-001, I-STORAGE-002, REQ-TOP-008)")
class SpoolVolumePersistenceIT {

    @Test
    @DisplayName("REQ-TOP-008 & I-STORAGE-002: Journal segments must persist across pod crash and recover on restart")
    void shouldPersistAcrossPodCrashAndRecoverOnRestart(@TempDir Path persistentVolumeDir) throws Exception {
        UUID op1 = UUID.randomUUID();
        UUID op2 = UUID.randomUUID();
        UUID op3 = UUID.randomUUID();

        // --- PHASE 1: Pod 1 writes commands to mounted persistent volume and terminates ---
        SegmentedFileJournal pod1Journal = new SegmentedFileJournal(
                persistentVolumeDir,
                64 * 1024L,
                10 * 1024 * 1024L,
                10,
                1
        );
        pod1Journal.start();

        CompletableFuture<JournalRecord> f1 = pod1Journal.append(CommandType.TRANSFER, op1, "{\"amount\": 100.00}".getBytes());
        CompletableFuture<JournalRecord> f2 = pod1Journal.append(CommandType.DEPOSIT, op2, "{\"amount\": 250.00}".getBytes());
        CompletableFuture<JournalRecord> f3 = pod1Journal.append(CommandType.WITHDRAW, op3, "{\"amount\": 50.00}".getBytes());

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);
        f3.get(5, TimeUnit.SECONDS);

        // Simulate crash / restart: close Pod 1 journal, releasing OS file lock
        pod1Journal.close();

        // Assert WAL files exist on disk in persistent directory
        List<Path> persistedSegments = pod1Journal.listSegmentFiles();
        assertThat(persistedSegments).isNotEmpty();

        // --- PHASE 2: Pod 2 starts up, mounts same volume, and executes JournalRecoveryWorker ---
        SegmentedFileJournal pod2Journal = new SegmentedFileJournal(
                persistentVolumeDir,
                64 * 1024L,
                10 * 1024 * 1024L,
                10,
                1
        );
        pod2Journal.start();

        java.util.List<CommandEnvelope> published = new java.util.concurrent.CopyOnWriteArrayList<>();
        EdgeCommandPublisher publisher = cmd -> {
            published.add(cmd);
            return CompletableFuture.completedFuture(null);
        };

        SpoolAckTracker ackTracker = new SpoolAckTracker();
        EdgeReadinessHealthIndicator healthIndicator = new EdgeReadinessHealthIndicator();

        JournalRecoveryWorker recoveryWorker = new JournalRecoveryWorker(
                pod2Journal,
                publisher,
                ackTracker,
                healthIndicator,
                0.80,
                0.20
        );

        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.INITIALIZING);

        // Run recovery scan
        recoveryWorker.runRecoveryScan();

        // Verify Pod 2 readiness transitions to READY
        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.READY);
        assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("UP");

        // Verify all 3 unACKed commands from Pod 1 were recovered and replayed to NATS
        assertThat(published).extracting(CommandEnvelope::operationId)
                .containsExactly(op1, op2, op3);

        // --- PHASE 3: Single-Writer Isolation Gate (I-STORAGE-001) ---
        // A second concurrent pod attempting to mount the same volume MUST fail fast
        SegmentedFileJournal roguePodJournal = new SegmentedFileJournal(
                persistentVolumeDir,
                64 * 1024L,
                10 * 1024 * 1024L,
                10,
                1
        );
        assertThatThrownBy(roguePodJournal::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already locked by active Edge instance");

        pod2Journal.close();
    }
}

