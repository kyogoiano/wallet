package br.com.wallet.integration.edge;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.internal.journal.segmented.BinaryRecordCodec;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Edge Graceful Shutdown Lifecycle Integration Test (I-LIFECYCLE-001, REQ-TOP-010, REQ-TOP-011)")
class GracefulShutdownIT {

    @Test
    @DisplayName("REQ-TOP-011 & I-LIFECYCLE-001: Shutdown ordering must mark readiness OUT_OF_SERVICE and flush in-flight batch durably")
    void shouldFlushBatchOnShutdownSignal(@TempDir Path spoolDir) throws Exception {
        EdgeReadinessHealthIndicator healthIndicator = new EdgeReadinessHealthIndicator();
        healthIndicator.markReady();
        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.READY);

        SegmentedFileJournal journal = new SegmentedFileJournal(
                spoolDir,
                64 * 1024L,
                10 * 1024 * 1024L,
                50,
                500L // 500ms batch delay so records are queued in active batch
        );
        journal.start();

        UUID opId = UUID.randomUUID();
        byte[] payload = "{\"amount\": 999.99}".getBytes();

        // 1. Queue command in active batch
        CompletableFuture<JournalRecord> writeFuture = journal.append(CommandType.TRANSFER, opId, payload);

        // 2. Simulate SIGTERM shutdown lifecycle sequence:
        // Step A: Mark readiness OUT_OF_SERVICE (Halts orchestrator routing / K8s readiness probe)
        healthIndicator.markOutOfService();
        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.OUT_OF_SERVICE);
        assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("OUT_OF_SERVICE");

        // Step B: Flush and close journal (forces pending group-commit batch to disk via FileChannel.force(false))
        journal.close();

        // Step C: Verify write future completed durably
        JournalRecord completedRecord = writeFuture.get(2, TimeUnit.SECONDS);
        assertThat(completedRecord.operationId()).isEqualTo(opId);

        // 3. Boundary Gate: New writes after shutdown must be rejected
        CompletableFuture<JournalRecord> rejectedFuture = journal.append(CommandType.DEPOSIT, UUID.randomUUID(), payload);
        assertThatThrownBy(rejectedFuture::join)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");

        // 4. Verification: Re-read disk segments from scratch using BinaryRecordCodec and verify CRC32C integrity
        List<Path> segmentFiles = journal.listSegmentFiles();
        assertThat(segmentFiles).hasSize(1);

        List<JournalRecord> recordsOnDisk = journal.readSegmentRecords(segmentFiles.getFirst());
        assertThat(recordsOnDisk).hasSize(1);
        assertThat(recordsOnDisk.getFirst().operationId()).isEqualTo(opId);
        assertThat(recordsOnDisk.getFirst().commandType()).isEqualTo(CommandType.TRANSFER);
    }
}
