package br.com.wallet.edge.internal.journal.segmented;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.CorruptedJournalException;
import br.com.wallet.edge.internal.journal.SpoolWatermarkGate;
import br.com.wallet.edge.internal.journal.spi.DurableSpilloverJournal;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Segmented append-only spillover journal managing preallocated chunks (default 64MB).
 * Invariants (REQ-EDG-002, REQ-EDG-007, REQ-EDG-008, I-EDGE-001, I-EDGE-005):
 * - Segments are preallocated and zero-filled with FileChannel.force(true).
 * - Sequential appends are group-committed with FileChannel.force(false) without metadata sync penalty.
 * - Enforces spool capacity watermarks with hysteresis.
 */
public class SegmentedFileJournal implements DurableSpilloverJournal {

    public static final long DEFAULT_SEGMENT_SIZE_BYTES = 64L * 1024 * 1024; // 64MB
    public static final long DEFAULT_MAX_SPOOL_BYTES = 10L * 1024 * 1024 * 1024; // 10GB
    public static final int DEFAULT_MAX_BATCH_SIZE = 100;
    public static final long DEFAULT_BATCH_WAIT_MS = 1L;

    private final Path spoolDirectory;
    private final long maxSegmentSizeBytes;
    private final long maxSpoolCapacityBytes;
    private final BinaryRecordCodec codec;
    private final SpoolWatermarkGate watermarkGate;
    private final GroupCommitEngine groupCommitEngine;

    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final AtomicLong currentSegmentId = new AtomicLong(0);

    private final ReentrantLock writeLock = new ReentrantLock();
    private FileChannel activeChannel;
    private Path activeSegmentPath;
    private long currentSegmentOffset;
    private volatile boolean closed = false;

    public SegmentedFileJournal(Path spoolDirectory) throws IOException {
        this(spoolDirectory, DEFAULT_SEGMENT_SIZE_BYTES, DEFAULT_MAX_SPOOL_BYTES, DEFAULT_MAX_BATCH_SIZE, DEFAULT_BATCH_WAIT_MS);
    }

    public SegmentedFileJournal(
            Path spoolDirectory,
            long maxSegmentSizeBytes,
            long maxSpoolCapacityBytes,
            int maxBatchSize,
            long maxBatchWaitMillis
    ) throws IOException {
        this.spoolDirectory = Objects.requireNonNull(spoolDirectory, "spoolDirectory must not be null");
        this.maxSegmentSizeBytes = maxSegmentSizeBytes;
        this.maxSpoolCapacityBytes = maxSpoolCapacityBytes;
        this.codec = new BinaryRecordCodec();
        this.watermarkGate = new SpoolWatermarkGate();

        Files.createDirectories(spoolDirectory);

        this.groupCommitEngine = new GroupCommitEngine(maxBatchSize, maxBatchWaitMillis, this::flushBatch);
    }

    public void start() throws IOException {
        writeLock.lock();
        try {
            recoverOrInitActiveSegment();
            groupCommitEngine.start();
        } finally {
            writeLock.unlock();
        }
    }

    private void recoverOrInitActiveSegment() throws IOException {
        List<Path> existingSegments = listSegmentFiles();
        if (existingSegments.isEmpty()) {
            createNewSegment(1L);
        } else {
            // Pick highest segment
            Path lastSegment = existingSegments.getLast();
            String name = lastSegment.getFileName().toString();
            long segId = parseSegmentId(name);
            currentSegmentId.set(segId);
            activeSegmentPath = lastSegment;
            activeChannel = FileChannel.open(activeSegmentPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
            
            // Scan to find current offset and highest sequence number
            scanOffsetAndSequence(activeSegmentPath);
        }
    }

    private void createNewSegment(long segId) throws IOException {
        if (activeChannel != null && activeChannel.isOpen()) {
            activeChannel.force(true);
            activeChannel.close();
        }

        currentSegmentId.set(segId);
        activeSegmentPath = spoolDirectory.resolve(String.format("segment-%016d.wal", segId));

        activeChannel = FileChannel.open(
                activeSegmentPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );

        // 1. Write 32B SegmentHeader
        SegmentHeader header = SegmentHeader.create(segId);
        ByteBuffer headerBuf = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
        header.writeTo(headerBuf);
        headerBuf.flip();
        activeChannel.write(headerBuf, 0);

        // 2. Preallocate segment file to maxSegmentSizeBytes and commit inode metadata
        activeChannel.truncate(maxSegmentSizeBytes);
        ByteBuffer zero = ByteBuffer.allocate(1);
        zero.put((byte) 0);
        zero.flip();
        activeChannel.write(zero, maxSegmentSizeBytes - 1);
        activeChannel.force(true);

        currentSegmentOffset = SegmentHeader.HEADER_SIZE;
    }

    private void scanOffsetAndSequence(Path segment) throws IOException {
        try (FileChannel ch = FileChannel.open(segment, StandardOpenOption.READ)) {
            ByteBuffer headerBuf = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
            ch.read(headerBuf, 0);
            headerBuf.flip();
            SegmentHeader.readFrom(headerBuf);

            long offset = SegmentHeader.HEADER_SIZE;
            ByteBuffer magicBuf = ByteBuffer.allocate(4);

            while (offset + BinaryRecordCodec.HEADER_SIZE <= maxSegmentSizeBytes) {
                magicBuf.clear();
                int read = ch.read(magicBuf, offset);
                if (read < 4) break;
                magicBuf.flip();
                int magic = magicBuf.getInt();
                if (magic == 0) {
                    // Preallocated zero boundary reached
                    break;
                }
                if (magic != BinaryRecordCodec.MAGIC) {
                    throw new CorruptedJournalException("Corrupted record magic 0x" + Integer.toHexString(magic) + " at offset " + offset);
                }

                // Read total length from offset + 8
                ByteBuffer lenBuf = ByteBuffer.allocate(4);
                ch.read(lenBuf, offset + 8);
                lenBuf.flip();
                int totalLength = lenBuf.getInt();
                if (totalLength < BinaryRecordCodec.HEADER_SIZE) {
                    throw new CorruptedJournalException("Invalid record length " + totalLength + " at offset " + offset);
                }

                // Read sequence number from offset + 16
                ByteBuffer seqBuf = ByteBuffer.allocate(8);
                ch.read(seqBuf, offset + 16);
                seqBuf.flip();
                long seq = seqBuf.getLong();
                sequenceGenerator.accumulateAndGet(seq, Math::max);

                offset += totalLength;
            }
            currentSegmentOffset = offset;
        }
    }

    @Override
    public CompletableFuture<JournalRecord> append(CommandType type, UUID operationId, byte[] payload) {
        if (closed) {
            CompletableFuture<JournalRecord> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("SegmentedFileJournal is closed"));
            return failed;
        }

        // Check spool capacity watermarks (REQ-EDG-003, I-EDGE-005)
        watermarkGate.evaluate(spoolUsagePercent());
        if (!watermarkGate.isDegradedAcceptanceAllowed()) {
            CompletableFuture<JournalRecord> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Spool journal is SATURATED (>= 95%). Degraded acceptance rejected."));
            return failed;
        }

        long seq = sequenceGenerator.incrementAndGet();
        JournalRecord record = new JournalRecord(seq, operationId, System.currentTimeMillis(), type, payload);
        return groupCommitEngine.submit(record);
    }

    private void flushBatch(List<JournalRecord> records, ByteBuffer buffer) throws Exception {
        writeLock.lock();
        try {
            int bytesToWrite = buffer.remaining();

            // Check if buffer fits in current segment
            if (currentSegmentOffset + bytesToWrite > maxSegmentSizeBytes) {
                createNewSegment(currentSegmentId.get() + 1);
            }

            // Write all bytes to FileChannel at currentSegmentOffset
            long startOffset = currentSegmentOffset;
            while (buffer.hasRemaining()) {
                int written = activeChannel.write(buffer, currentSegmentOffset);
                currentSegmentOffset += written;
            }

            // Group Commit Policy: force(false) inside preallocated file avoids inode metadata sync penalty
            activeChannel.force(false);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long currentSpoolUsageBytes() {
        try (Stream<Path> stream = Files.list(spoolDirectory)) {
            return stream.filter(p -> p.toString().endsWith(".wal"))
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public long maxSpoolCapacityBytes() {
        return maxSpoolCapacityBytes;
    }

    @Override
    public List<Path> listSegmentFiles() {
        try (Stream<Path> stream = Files.list(spoolDirectory)) {
            return stream.filter(p -> p.toString().endsWith(".wal"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public List<JournalRecord> readSegmentRecords(Path segmentFile) {
        List<JournalRecord> records = new ArrayList<>();
        try (FileChannel ch = FileChannel.open(segmentFile, StandardOpenOption.READ)) {
            // Verify segment header
            ByteBuffer headerBuf = ByteBuffer.allocate(SegmentHeader.HEADER_SIZE);
            int bytesRead = ch.read(headerBuf, 0);
            if (bytesRead < SegmentHeader.HEADER_SIZE) {
                throw new CorruptedJournalException("Truncated segment header in " + segmentFile);
            }
            headerBuf.flip();
            SegmentHeader.readFrom(headerBuf);

            long offset = SegmentHeader.HEADER_SIZE;
            long fileSize = ch.size();

            while (offset + BinaryRecordCodec.HEADER_SIZE <= fileSize) {
                // Peek magic
                ByteBuffer magicBuf = ByteBuffer.allocate(4);
                ch.read(magicBuf, offset);
                magicBuf.flip();
                int magic = magicBuf.getInt();

                if (magic == 0) {
                    // Reached end of committed records in preallocated block
                    break;
                }

                if (magic != BinaryRecordCodec.MAGIC) {
                    throw new CorruptedJournalException("Invalid record magic 0x" + Integer.toHexString(magic) + " at offset " + offset);
                }

                // Peek total length
                ByteBuffer lenBuf = ByteBuffer.allocate(4);
                ch.read(lenBuf, offset + 8);
                lenBuf.flip();
                int totalLength = lenBuf.getInt();

                if (totalLength < BinaryRecordCodec.HEADER_SIZE || offset + totalLength > fileSize) {
                    throw new CorruptedJournalException("Invalid record length " + totalLength + " at offset " + offset);
                }

                ByteBuffer recordBuf = ByteBuffer.allocate(totalLength);
                ch.read(recordBuf, offset);
                recordBuf.flip();

                JournalRecord record = codec.decode(recordBuf);
                records.add(record);

                offset += totalLength;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read segment records from " + segmentFile, e);
        }
        return records;
    }

    @Override
    public void quarantineSegment(Path segmentFile, String reason) {
        writeLock.lock();
        try {
            if (activeSegmentPath != null && activeSegmentPath.equals(segmentFile)) {
                if (activeChannel != null && activeChannel.isOpen()) {
                    activeChannel.close();
                }
            }
            Path quarantinedPath = spoolDirectory.resolve(segmentFile.getFileName().toString() + ".corrupt");
            Files.move(segmentFile, quarantinedPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to quarantine segment: " + segmentFile, e);
        } finally {
            writeLock.unlock();
        }
    }

    private long parseSegmentId(String filename) {
        try {
            // Expects "segment-0000000000000001.wal"
            String numberPart = filename.replace("segment-", "").replace(".wal", "");
            return Long.parseLong(numberPart);
        } catch (Exception e) {
            return 1L;
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        groupCommitEngine.close();
        writeLock.lock();
        try {
            if (activeChannel != null && activeChannel.isOpen()) {
                activeChannel.force(true);
                activeChannel.close();
            }
        } finally {
            writeLock.unlock();
        }
    }
}
