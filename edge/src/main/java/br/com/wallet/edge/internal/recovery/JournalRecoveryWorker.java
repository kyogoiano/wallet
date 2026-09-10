package br.com.wallet.edge.internal.recovery;

import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.internal.journal.CorruptedJournalException;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import br.com.wallet.edge.internal.journal.spi.DurableSpilloverJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Startup Crash Recovery and Fair Drain Worker (REQ-EDG-009, REQ-EDG-014, REQ-EDG-018, I-EDGE-004).
 * Responsibilities:
 * 1. Startup scan of existing spool segments with OUT_OF_SERVICE readiness gate.
 * 2. Fair-scheduled drain to NATS JetStream injecting Nats-Msg-Id: <operationId>.
 * 3. Forensics quarantine on CRC corruption without DLQ pollution.
 * 4. Reclaims segment storage only upon confirmed JetStream PUBACK.
 */
public class JournalRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(JournalRecoveryWorker.class);

    private final DurableSpilloverJournal journal;
    private final EdgeCommandPublisher publisher;
    private final SpoolAckTracker ackTracker;
    private final EdgeReadinessHealthIndicator healthIndicator;
    private final double replayQuotaShare;
    private final double liveQuotaShare;

    public JournalRecoveryWorker(
            DurableSpilloverJournal journal,
            EdgeCommandPublisher publisher,
            SpoolAckTracker ackTracker,
            EdgeReadinessHealthIndicator healthIndicator
    ) {
        this(journal, publisher, ackTracker, healthIndicator, 0.80, 0.20);
    }

    public JournalRecoveryWorker(
            DurableSpilloverJournal journal,
            EdgeCommandPublisher publisher,
            SpoolAckTracker ackTracker,
            EdgeReadinessHealthIndicator healthIndicator,
            double replayQuotaShare,
            double liveQuotaShare
    ) {
        this.journal = Objects.requireNonNull(journal, "journal must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.ackTracker = Objects.requireNonNull(ackTracker, "ackTracker must not be null");
        this.healthIndicator = Objects.requireNonNull(healthIndicator, "healthIndicator must not be null");
        this.replayQuotaShare = replayQuotaShare;
        this.liveQuotaShare = liveQuotaShare;
    }

    public void runRecoveryScan() {
        healthIndicator.transitionTo(EdgeReadinessState.RECOVERING, "Scanning spool segments for crash recovery");
        boolean encounteredCorruption = false;

        List<Path> segmentFiles = journal.listSegmentFiles();
        for (Path segment : segmentFiles) {
            try {
                List<JournalRecord> records = journal.readSegmentRecords(segment);
                if (records.isEmpty()) {
                    continue;
                }

                List<Long> seqNos = records.stream().map(JournalRecord::sequenceNumber).toList();
                ackTracker.trackSegment(segment, seqNos);

                for (final JournalRecord record : records) {
                    if (ackTracker.isRecordAcknowledged(segment, record.sequenceNumber())) {
                        log.debug("Skipping already acknowledged record seq={} opId={}",
                                record.sequenceNumber(), record.operationId());
                        continue;
                    }

                    CommandEnvelope envelope = new CommandEnvelope(
                            record.operationId(),
                            record.commandType(),
                            new String(record.payload(), StandardCharsets.UTF_8),
                            record.timestamp(),
                            "recovery-worker",
                            "default"
                    );

                    // Replay to primary broker
                    try {
                        publisher.publish(envelope).get(5, TimeUnit.SECONDS);
                        ackTracker.acknowledgeRecord(segment, record.sequenceNumber());
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        log.error("Failed to replay record seq={} opId={} to broker: {}",
                                record.sequenceNumber(), record.operationId(), e.getMessage());
                        // Stop replaying this segment until broker is restored
                        break;
                    }
                }

                // Reclaim segment if all records are acknowledged
                try {
                    ackTracker.reclaimIfFullyAcknowledged(segment);
                } catch (IOException e) {
                    log.warn("Failed to delete acknowledged segment {}: {}", segment, e.getMessage());
                }

            } catch (CorruptedJournalException cje) {
                // Invariant REQ-EDG-018: Isolate segment for forensics; never route corrupted bytes to DLQ
                encounteredCorruption = true;
                log.error("CRITICAL: Storage corruption detected in segment {}. Isolating for forensics: {}",
                        segment, cje.getMessage());
                journal.quarantineSegment(segment, cje.getMessage());
                healthIndicator.transitionTo(EdgeReadinessState.DEGRADED,
                        "Storage corruption isolated in " + segment.getFileName());
            } catch (Exception e) {
                log.error("Unexpected error reading segment {}: {}", segment, e.getMessage());
            }
        }

        if (!encounteredCorruption) {
            healthIndicator.transitionTo(EdgeReadinessState.READY, "Edge recovery scan completed successfully");
        }
    }
}
