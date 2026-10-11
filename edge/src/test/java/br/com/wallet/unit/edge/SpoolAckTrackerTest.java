package br.com.wallet.unit.edge;

import br.com.wallet.edge.internal.recovery.SpoolAckTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpoolAckTracker Unit Tests (TASK-4.4, TASK-4.5, TASK-4.6, REQ-EDG-016)")
class SpoolAckTrackerTest {

    @Test
    @DisplayName("Should only reclaim segment after all records have confirmed durable PUBACK (REQ-EDG-016)")
    void shouldOnlyReclaimAfterPubAck(@TempDir Path tempDir) throws IOException {
        Path segmentFile = tempDir.resolve("segment-00000001.wal");
        Files.writeString(segmentFile, "test-segment-content");

        SpoolAckTracker tracker = new SpoolAckTracker();
        tracker.trackSegment(segmentFile, List.of(101L, 102L));

        assertThat(tracker.getPendingCount(segmentFile)).isEqualTo(2);
        assertThat(tracker.isSegmentFullyAcknowledged(segmentFile)).isFalse();

        // Reclaim attempt before all acks -> must return false, file remains
        boolean reclaimedPartial = tracker.reclaimIfFullyAcknowledged(segmentFile);
        assertThat(reclaimedPartial).isFalse();
        assertThat(Files.exists(segmentFile)).isTrue();

        // Ack first record
        tracker.acknowledgeRecord(segmentFile, 101L);
        assertThat(tracker.getPendingCount(segmentFile)).isEqualTo(1);
        assertThat(tracker.isSegmentFullyAcknowledged(segmentFile)).isFalse();
        assertThat(tracker.reclaimIfFullyAcknowledged(segmentFile)).isFalse();
        assertThat(Files.exists(segmentFile)).isTrue();

        // Ack second record -> now fully acknowledged
        tracker.acknowledgeRecord(segmentFile, 102L);
        assertThat(tracker.getPendingCount(segmentFile)).isEqualTo(0);
        assertThat(tracker.isSegmentFullyAcknowledged(segmentFile)).isTrue();

        // Now reclaim must delete the file
        boolean reclaimedFinal = tracker.reclaimIfFullyAcknowledged(segmentFile);
        assertThat(reclaimedFinal).isTrue();
        assertThat(Files.exists(segmentFile)).isFalse();
    }

    @Test
    @DisplayName("Should persist ACK checkpoints across restart so acknowledged records are distinguished (TASK-4.5, TASK-4.6)")
    void shouldDistinguishAcknowledgedRecordsAfterRestart(@TempDir Path tempDir) throws IOException {
        Path segmentFile = tempDir.resolve("segment-00000002.wal");
        Files.writeString(segmentFile, "test-wal-data");

        SpoolAckTracker tracker1 = new SpoolAckTracker();
        tracker1.trackSegment(segmentFile, List.of(1L, 2L, 3L));

        // Acknowledge record 1 and 2
        tracker1.acknowledgeRecord(segmentFile, 1L);
        tracker1.acknowledgeRecord(segmentFile, 2L);

        assertThat(tracker1.isRecordAcknowledged(segmentFile, 1L)).isTrue();
        assertThat(tracker1.isRecordAcknowledged(segmentFile, 2L)).isTrue();
        assertThat(tracker1.isRecordAcknowledged(segmentFile, 3L)).isFalse();
        assertThat(tracker1.getPendingCount(segmentFile)).isEqualTo(1);

        // Simulate crash / process restart: create a new SpoolAckTracker instance
        SpoolAckTracker trackerAfterRestart = new SpoolAckTracker();
        trackerAfterRestart.trackSegment(segmentFile, List.of(1L, 2L, 3L));

        // After restart, records 1 and 2 must remain marked as acknowledged
        assertThat(trackerAfterRestart.isRecordAcknowledged(segmentFile, 1L)).isTrue();
        assertThat(trackerAfterRestart.isRecordAcknowledged(segmentFile, 2L)).isTrue();
        assertThat(trackerAfterRestart.isRecordAcknowledged(segmentFile, 3L)).isFalse();
        assertThat(trackerAfterRestart.getPendingCount(segmentFile)).isEqualTo(1);

        // Acknowledge the remaining record 3
        trackerAfterRestart.acknowledgeRecord(segmentFile, 3L);
        assertThat(trackerAfterRestart.isSegmentFullyAcknowledged(segmentFile)).isTrue();

        // Reclaim deletes both the segment and the checkpoint metadata
        assertThat(trackerAfterRestart.reclaimIfFullyAcknowledged(segmentFile)).isTrue();
        assertThat(Files.exists(segmentFile)).isFalse();
    }
}
