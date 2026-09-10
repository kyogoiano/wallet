package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.segmented.BinaryRecordCodec;
import br.com.wallet.edge.internal.journal.segmented.GroupCommitEngine;
import br.com.wallet.edge.internal.journal.segmented.JournalRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GroupCommitEngine Unit Tests (TASK-2.1, REQ-EDG-008, I-EDGE-001)")
class GroupCommitEngineTest {

    private Path tempDir;
    private BinaryRecordCodec codec;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("group-commit-test-");
        codec = new BinaryRecordCodec();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    private JournalRecord sampleRecord(long seqNo) {
        return new JournalRecord(seqNo, UUID.randomUUID(), System.currentTimeMillis(), CommandType.TRANSFER, ("{\"seq\":" + seqNo + "}").getBytes());
    }

    @Test
    @DisplayName("Zero futures must complete before durable fsync execution (I-EDGE-001)")
    void shouldCompleteFuturesOnlyAfterFsync() throws Exception {
        CountDownLatch fsyncStarted = new CountDownLatch(1);
        CountDownLatch allowFsyncToComplete = new CountDownLatch(1);
        AtomicBoolean fsyncCompleted = new AtomicBoolean(false);

        GroupCommitEngine engine = new GroupCommitEngine(
                100,
                5,
                (records, buffer) -> {
                    fsyncStarted.countDown();
                    try {
                        allowFsyncToComplete.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}
                    fsyncCompleted.set(true);
                }
        );

        try (engine) {
            engine.start();
            JournalRecord record = sampleRecord(1L);
            CompletableFuture<JournalRecord> future = engine.submit(record);

            // Wait until fsync starts
            assertThat(fsyncStarted.await(1, TimeUnit.SECONDS)).isTrue();

            // At this point, fsync has NOT completed yet, so future MUST NOT be done
            assertThat(future.isDone()).isFalse();
            assertThat(fsyncCompleted.get()).isFalse();

            // Allow fsync to finish
            allowFsyncToComplete.countDown();

            // Future must now complete successfully
            JournalRecord committed = future.get(1, TimeUnit.SECONDS);
            assertThat(committed).isEqualTo(record);
            assertThat(fsyncCompleted.get()).isTrue();
        }
    }

    @Test
    @DisplayName("Should flush immediately when batch size reaches 100 without waiting for timeout")
    void shouldFlushImmediatelyOnBatchThreshold() throws Exception {
        AtomicInteger flushCount = new AtomicInteger(0);
        CountDownLatch batchFlushed = new CountDownLatch(1);

        GroupCommitEngine engine = new GroupCommitEngine(
                100,
                5000, // Very long timer (5s) to prove threshold trigger
                (records, buffer) -> {
                    flushCount.incrementAndGet();
                    if (records.size() == 100) {
                        batchFlushed.countDown();
                    }
                }
        );

        try (engine) {
            engine.start();
            List<CompletableFuture<JournalRecord>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(engine.submit(sampleRecord(i)));
            }

            // Must flush immediately on reaching 100
            assertThat(batchFlushed.await(1, TimeUnit.SECONDS)).isTrue();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(1, TimeUnit.SECONDS);
            assertThat(flushCount.get()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("Should flush batch on elapsed timer (1ms) when below threshold")
    void shouldFlushOnElapsedTimer() throws Exception {
        CountDownLatch flushedLatch = new CountDownLatch(1);

        GroupCommitEngine engine = new GroupCommitEngine(
                100,
                1, // 1ms flush interval
                (records, buffer) -> flushedLatch.countDown()
        );

        try (engine) {
            engine.start();
            CompletableFuture<JournalRecord> future = engine.submit(sampleRecord(42L));
            assertThat(flushedLatch.await(500, TimeUnit.MILLISECONDS)).isTrue();
            JournalRecord committed = future.get(500, TimeUnit.MILLISECONDS);
            assertThat(committed.sequenceNumber()).isEqualTo(42L);
        }
    }

    @Test
    @DisplayName("Should complete all pending futures exceptionally when fsync fails")
    void shouldFailAllPendingFuturesWhenFsyncFails() {
        GroupCommitEngine engine = new GroupCommitEngine(
                10,
                1,
                (records, buffer) -> {
                    throw new IOException("Simulated disk I/O error");
                }
        );

        try (engine) {
            engine.start();
            CompletableFuture<JournalRecord> future1 = engine.submit(sampleRecord(1L));
            CompletableFuture<JournalRecord> future2 = engine.submit(sampleRecord(2L));

            assertThatThrownBy(future1::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IOException.class);

            assertThatThrownBy(future2::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IOException.class);
        }
    }
}
