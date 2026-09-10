package br.com.wallet.edge.internal.journal.segmented;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance group commit engine implementing durable fsync batching.
 * Invariants (REQ-EDG-008, I-EDGE-001):
 * - Flushes immediately when batch reaches maxBatchSize OR elapsed wait reaches maxBatchWaitMillis.
 * - Caller futures complete ONLY after durable flush and fsync succeed.
 */
public class GroupCommitEngine implements Closeable {

    @FunctionalInterface
    public interface BatchFlusher {
        void flush(List<JournalRecord> records, ByteBuffer buffer) throws Exception;
    }

    public record PendingRecord(
            JournalRecord record,
            CompletableFuture<JournalRecord> future
    ) {}

    private final int maxBatchSize;
    private final long maxBatchWaitMillis;
    private final BatchFlusher flusher;
    private final BinaryRecordCodec codec;
    private final BlockingQueue<PendingRecord> queue;
    private final AtomicBoolean running;
    private Thread flusherThread;

    public GroupCommitEngine(int maxBatchSize, long maxBatchWaitMillis, BatchFlusher flusher) {
        this.maxBatchSize = maxBatchSize;
        this.maxBatchWaitMillis = maxBatchWaitMillis;
        this.flusher = flusher;
        this.codec = new BinaryRecordCodec();
        this.queue = new LinkedBlockingQueue<>();
        this.running = new AtomicBoolean(false);
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            flusherThread = Thread.ofPlatform()
                    .daemon(true)
                    .name("edge-group-commit-flusher")
                    .start(this::flusherLoop);
        }
    }

    public CompletableFuture<JournalRecord> submit(JournalRecord record) {
        if (!running.get()) {
            CompletableFuture<JournalRecord> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("GroupCommitEngine is stopped"));
            return failed;
        }

        CompletableFuture<JournalRecord> future = new CompletableFuture<>();
        queue.offer(new PendingRecord(record, future));
        return future;
    }

    private void flusherLoop() {
        List<PendingRecord> batch = new ArrayList<>(maxBatchSize);
        ByteBuffer buffer = ByteBuffer.allocate(maxBatchSize * (BinaryRecordCodec.HEADER_SIZE + 4096));

        while (running.get() || !queue.isEmpty()) {
            try {
                // Wait for the first record of a batch
                PendingRecord first = queue.poll(100, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }

                batch.add(first);
                queue.drainTo(batch, maxBatchSize - batch.size());

                long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxBatchWaitMillis);

                // Accumulate records until maxBatchSize is reached OR elapsed wait time expires
                while (batch.size() < maxBatchSize) {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        break;
                    }
                    PendingRecord next = queue.poll(remainingNanos, TimeUnit.NANOSECONDS);
                    if (next == null) {
                        break;
                    }
                    batch.add(next);
                    queue.drainTo(batch, maxBatchSize - batch.size());
                }

                processBatch(batch, buffer);
                batch.clear();
                buffer.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Unexpected loop error
            }
        }

        // Drain any remaining records upon shutdown
        if (!queue.isEmpty()) {
            queue.drainTo(batch);
            if (!batch.isEmpty()) {
                processBatch(batch, buffer);
            }
        }
    }

    private void processBatch(List<PendingRecord> batch, ByteBuffer buffer) {
        List<JournalRecord> records = new ArrayList<>(batch.size());
        buffer.clear();

        // Calculate required buffer size and encode
        int totalBytes = 0;
        for (PendingRecord item : batch) {
            totalBytes += codec.calculateRecordSize(item.record());
        }

        if (buffer.capacity() < totalBytes) {
            buffer = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, totalBytes));
        }

        for (PendingRecord item : batch) {
            records.add(item.record());
            codec.encode(item.record(), buffer);
        }
        buffer.flip();

        try {
            // Durable flush and fsync executed synchronously
            flusher.flush(records, buffer);

            // Invariant I-EDGE-001: complete futures ONLY after flush succeeds
            for (PendingRecord item : batch) {
                item.future().complete(item.record());
            }
        } catch (Throwable error) {
            // Fail all futures in batch exceptionally
            for (PendingRecord item : batch) {
                item.future().completeExceptionally(error);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            if (flusherThread != null) {
                flusherThread.interrupt();
                try {
                    flusherThread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
