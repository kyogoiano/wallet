package br.com.wallet.edge.internal.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spool Ack Tracker (REQ-EDG-016, TASK-4.4, TASK-4.5, TASK-4.6).
 * Enforces that journal records and segment files remain recoverable until JetStream
 * confirms durable PUBACK. Segments are never reclaimed prematurely.
 * Persists record-level ACK checkpoints durably to disk so that acknowledged records
 * remain distinguishable from unacknowledged records after a process restart.
 */
public class SpoolAckTracker {

    private static final Logger log = LoggerFactory.getLogger(SpoolAckTracker.class);
    private static final String ACK_SUFFIX = ".ack";

    private final ConcurrentHashMap<Path, Set<Long>> pendingAcksBySegment = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, Set<Long>> acknowledgedBySegment = new ConcurrentHashMap<>();

    public void trackSegment(Path segmentFile, List<Long> sequenceNumbers) {
        if (sequenceNumbers.isEmpty()) return;

        Set<Long> durablyAcknowledged = loadDurableAcks(segmentFile);
        Set<Long> acknowledged = ConcurrentHashMap.newKeySet();
        acknowledged.addAll(durablyAcknowledged);
        acknowledgedBySegment.put(segmentFile, acknowledged);

        Set<Long> pending = ConcurrentHashMap.newKeySet();
        for (Long seq : sequenceNumbers) {
            if (!durablyAcknowledged.contains(seq)) {
                pending.add(seq);
            }
        }
        pendingAcksBySegment.put(segmentFile, pending);
    }

    public void acknowledgeRecord(Path segmentFile, long sequenceNumber) {
        Set<Long> pending = pendingAcksBySegment.get(segmentFile);
        if (pending != null) {
            pending.remove(sequenceNumber);
        }
        Set<Long> acknowledged = acknowledgedBySegment.computeIfAbsent(segmentFile, k -> ConcurrentHashMap.newKeySet());
        if (acknowledged.add(sequenceNumber)) {
            persistAck(segmentFile, sequenceNumber);
        }
    }

    public boolean isRecordAcknowledged(Path segmentFile, long sequenceNumber) {
        Set<Long> acknowledged = acknowledgedBySegment.get(segmentFile);
        if (acknowledged != null && acknowledged.contains(sequenceNumber)) {
            return true;
        }
        Set<Long> pending = pendingAcksBySegment.get(segmentFile);
        return pending != null && !pending.contains(sequenceNumber);
    }

    public boolean isSegmentFullyAcknowledged(Path segmentFile) {
        Set<Long> pending = pendingAcksBySegment.get(segmentFile);
        return pending != null && pending.isEmpty();
    }

    public int getPendingCount(Path segmentFile) {
        Set<Long> pending = pendingAcksBySegment.get(segmentFile);
        return pending != null ? pending.size() : 0;
    }

    public boolean reclaimIfFullyAcknowledged(Path segmentFile) throws IOException {
        if (isSegmentFullyAcknowledged(segmentFile)) {
            pendingAcksBySegment.remove(segmentFile);
            acknowledgedBySegment.remove(segmentFile);
            Files.deleteIfExists(segmentFile);
            Path ackFile = getAckFilePath(segmentFile);
            Files.deleteIfExists(ackFile);
            return true;
        }
        return false;
    }

    private Path getAckFilePath(Path segmentFile) {
        return segmentFile.resolveSibling(segmentFile.getFileName().toString() + ACK_SUFFIX);
    }

    private Set<Long> loadDurableAcks(Path segmentFile) {
        Path ackFile = getAckFilePath(segmentFile);
        if (!Files.exists(ackFile)) {
            return Collections.emptySet();
        }
        try {
            List<String> lines = Files.readAllLines(ackFile);
            Set<Long> set = new HashSet<>(lines.size());
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        set.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return set;
        } catch (IOException e) {
            log.warn("Failed to read ACK checkpoint file {}: {}", ackFile, e.getMessage());
            return Collections.emptySet();
        }
    }

    private void persistAck(Path segmentFile, long sequenceNumber) {
        Path ackFile = getAckFilePath(segmentFile);
        try {
            Files.writeString(
                    ackFile,
                    sequenceNumber + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.warn("Failed to persist ACK checkpoint for segment {} seq {}: {}", segmentFile, sequenceNumber, e.getMessage());
        }
    }
}
