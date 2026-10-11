package br.com.wallet.edge.internal.journal.spi;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SPI interface for durable append-only spillover storage.
 * Mandated by REQ-EDG-002, REQ-EDG-007, and REQ-EDG-012.
 */
public interface DurableSpilloverJournal extends Closeable {

    /**
     * Appends a financial command to the journal and returns a future completing only
     * after durable group-commit fsync has succeeded on disk (I-EDGE-001).
     */
    CompletableFuture<JournalRecord> append(CommandType type, UUID operationId, byte[] payload);

    /**
     * Current storage utilization in bytes across all segments.
     */
    long currentSpoolUsageBytes();

    /**
     * Maximum configured spool capacity in bytes.
     */
    long maxSpoolCapacityBytes();

    /**
     * Storage utilization percentage (0.0 to 100.0).
     */
    default double spoolUsagePercent() {
        long max = maxSpoolCapacityBytes();
        if (max <= 0) return 0.0;
        return (double) currentSpoolUsageBytes() / max * 100.0;
    }

    /**
     * Lists active or completed segment file paths.
     */
    List<Path> listSegmentFiles();

    /**
     * Sequentially scans and reads records from a specific segment file.
     */
    List<JournalRecord> readSegmentRecords(Path segmentFile);

    /**
     * Isolates a corrupted segment for forensics (REQ-EDG-018).
     */
    void quarantineSegment(Path segmentFile, String reason);
}
