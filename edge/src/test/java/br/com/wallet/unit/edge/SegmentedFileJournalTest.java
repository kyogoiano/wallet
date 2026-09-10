package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.CorruptedJournalException;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SegmentedFileJournal Unit Tests (TASK-1.3, REQ-EDG-007, REQ-EDG-008, I-EDGE-001)")
class SegmentedFileJournalTest {

    private Path spoolDir;
    private SegmentedFileJournal journal;

    @BeforeEach
    void setUp() throws IOException {
        spoolDir = Files.createTempDirectory("edge-spool-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (journal != null) {
            journal.close();
        }
        if (spoolDir != null && Files.exists(spoolDir)) {
            try (var stream = Files.walk(spoolDir)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("Should create preallocated segment with valid 32B header and append records durably")
    void shouldCreateSegmentAndAppendRecords() throws Exception {
        // Small segment size for testing: 64KB
        long segmentSize = 64 * 1024L;
        journal = new SegmentedFileJournal(spoolDir, segmentSize, 10 * 1024 * 1024L, 10, 1);
        journal.start();

        UUID opId1 = UUID.randomUUID();
        UUID opId2 = UUID.randomUUID();
        byte[] payload1 = "{\"amount\": 100.00}".getBytes();
        byte[] payload2 = "{\"amount\": 250.50}".getBytes();

        CompletableFuture<JournalRecord> f1 = journal.append(CommandType.TRANSFER, opId1, payload1);
        CompletableFuture<JournalRecord> f2 = journal.append(CommandType.DEPOSIT, opId2, payload2);

        JournalRecord r1 = f1.get(2, TimeUnit.SECONDS);
        JournalRecord r2 = f2.get(2, TimeUnit.SECONDS);

        assertThat(r1.operationId()).isEqualTo(opId1);
        assertThat(r1.commandType()).isEqualTo(CommandType.TRANSFER);
        assertThat(r2.operationId()).isEqualTo(opId2);
        assertThat(r2.commandType()).isEqualTo(CommandType.DEPOSIT);

        // Verify segment files on disk
        List<Path> segments = journal.listSegmentFiles();
        assertThat(segments).hasSize(1);
        Path activeSegment = segments.get(0);
        assertThat(Files.size(activeSegment)).isEqualTo(segmentSize);

        // Read records back from segment
        List<JournalRecord> recovered = journal.readSegmentRecords(activeSegment);
        assertThat(recovered).hasSize(2);
        assertThat(recovered.get(0).operationId()).isEqualTo(opId1);
        assertThat(recovered.get(1).operationId()).isEqualTo(opId2);
    }

    @Test
    @DisplayName("Should roll over to next segment when current segment reaches capacity")
    void shouldRollOverSegmentOnCapacity() throws Exception {
        // Very small segment: 300 bytes (Header: 32B, each record: 54B + 50B payload = 104B)
        long segmentSize = 300L;
        journal = new SegmentedFileJournal(spoolDir, segmentSize, 10 * 1024 * 1024L, 1, 1);
        journal.start();

        byte[] payload = new byte[50];
        // Append 4 records: each is 104 bytes -> 32 + 104*2 = 240 <= 300; 3rd record will exceed 300 and trigger rollover
        for (int i = 0; i < 4; i++) {
            journal.append(CommandType.TRANSFER, UUID.randomUUID(), payload).get(2, TimeUnit.SECONDS);
        }

        List<Path> segments = journal.listSegmentFiles();
        assertThat(segments.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should quarantine segment when record CRC32C is corrupted (REQ-EDG-018)")
    void shouldQuarantineCorruptedSegment() throws Exception {
        long segmentSize = 16 * 1024L;
        journal = new SegmentedFileJournal(spoolDir, segmentSize, 10 * 1024 * 1024L, 1, 1);
        journal.start();

        UUID opId = UUID.randomUUID();
        journal.append(CommandType.WITHDRAW, opId, "test-data".getBytes()).get(2, TimeUnit.SECONDS);

        Path segment = journal.listSegmentFiles().get(0);

        // Corrupt record CRC at offset 44 (Segment header: 32B + record CRC at offset 12 = 44)
        try (FileChannel channel = FileChannel.open(segment, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.putInt(0xBAD0C0DE);
            buf.flip();
            channel.write(buf, 44);
            channel.force(true);
        }

        assertThatThrownBy(() -> journal.readSegmentRecords(segment))
                .isInstanceOf(CorruptedJournalException.class)
                .hasMessageContaining("CRC32C checksum mismatch");

        // Quarantine
        journal.quarantineSegment(segment, "Corrupted CRC detected in test");
        assertThat(Files.exists(segment)).isFalse();
        Path quarantined = spoolDir.resolve(segment.getFileName().toString() + ".corrupt");
        assertThat(Files.exists(quarantined)).isTrue();
    }
}
