package br.com.wallet.integration.edge;

import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import br.com.wallet.edge.internal.recovery.JournalRecoveryWorker;
import br.com.wallet.edge.internal.recovery.SpoolAckTracker;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EdgeCorruptionIT: Forensic Isolation On Bit-Rot Integration Test (TASK-6.3, REQ-EDG-018)")
class EdgeCorruptionIT {

    private Path spoolDir;
    private SegmentedFileJournal journal;
    private List<CommandEnvelope> dlqOrBrokerDeliveries;
    private EdgeCommandPublisher publisher;
    private SpoolAckTracker ackTracker;
    private EdgeReadinessHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() throws IOException {
        spoolDir = Files.createTempDirectory("edge-corruption-test-");
        journal = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 1, 1L);
        journal.start();

        dlqOrBrokerDeliveries = new ArrayList<>();
        publisher = command -> {
            dlqOrBrokerDeliveries.add(command);
            return CompletableFuture.completedFuture(null);
        };

        ackTracker = new SpoolAckTracker();
        healthIndicator = new EdgeReadinessHealthIndicator();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (journal != null) {
            journal.close();
        }
        if (spoolDir != null && Files.exists(spoolDir)) {
            try (var stream = Files.walk(spoolDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("Should quarantine corrupted segment to .corrupt file without routing bad data to broker or DLQ (REQ-EDG-018)")
    void shouldQuarantineCorruptedSegmentWithoutDlqPollution() throws Exception {
        // Step 1: Write a valid record to the journal
        UUID opId = UUID.randomUUID();
        journal.append(CommandType.TRANSFER, opId, "{\"amount\": 999.00}".getBytes()).get(2, TimeUnit.SECONDS);

        List<Path> segments = journal.listSegmentFiles();
        assertThat(segments).hasSize(1);
        Path activeSegment = segments.get(0);

        // Close active channel before manipulating disk file directly
        journal.close();

        // Step 2: Inject disk bit-rot / bit-flip in record payload (offset 86: 32B header + 54B record header = 86)
        try (FileChannel ch = FileChannel.open(activeSegment, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer flip = ByteBuffer.allocate(1);
            flip.put((byte) 0xFF);
            flip.flip();
            ch.write(flip, 86);
            ch.force(true);
        }

        // Re-open journal
        journal = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 1, 1L);
        // Note: do not start() so we don't append, just run recovery scan

        JournalRecoveryWorker worker = new JournalRecoveryWorker(
                journal,
                publisher,
                ackTracker,
                healthIndicator
        );

        // Step 3: Run recovery scan
        worker.runRecoveryScan();

        // Invariant REQ-EDG-018: Corrupted file must be renamed to .corrupt for forensic inspection
        Path quarantined = spoolDir.resolve(activeSegment.getFileName().toString() + ".corrupt");
        assertThat(Files.exists(quarantined)).isTrue();
        assertThat(Files.exists(activeSegment)).isFalse();

        // Invariant: Zero corrupted commands routed to broker or DLQ
        assertThat(dlqOrBrokerDeliveries).isEmpty();

        // Node readiness remains DEGRADED to trigger operational alerts
        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.DEGRADED);
        assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("DEGRADED");
    }
}
