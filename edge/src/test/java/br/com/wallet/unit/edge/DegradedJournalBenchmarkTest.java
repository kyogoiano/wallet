package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Degraded Journal Microbenchmark (TASK-6.5, REQ-EDG-008)")
class DegradedJournalBenchmarkTest {

    private Path spoolDir;
    private SegmentedFileJournal journal;

    @BeforeEach
    void setUp() throws IOException {
        spoolDir = Files.createTempDirectory("edge-journal-bench-");
        // 64MB segments, 1GB total capacity, 100-batch, 1ms max delay
        journal = new SegmentedFileJournal(spoolDir, 64 * 1024 * 1024L, 1024 * 1024 * 1024L, 100, 1L);
        journal.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (journal != null) {
            journal.close();
        }
        if (spoolDir != null && Files.exists(spoolDir)) {
            try (var stream = Files.walk(spoolDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("Measure throughput and P50/P95/P99 group-commit fsync latency (TASK-6.5)")
    void benchmarkJournalThroughputAndLatency() throws Exception {
        int totalOps = 5_000;
        int concurrency = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        byte[] payload = "{\"from\":\"11111111-1111-1111-1111-111111111111\",\"to\":\"22222222-2222-2222-2222-222222222222\",\"amount\":100.00}".getBytes(StandardCharsets.UTF_8);

        long[] latenciesNanos = new long[totalOps];
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[totalOps];

        long startTime = System.nanoTime();

        for (int i = 0; i < totalOps; i++) {
            final int index = i;
            UUID opId = UUID.randomUUID();
            long submitNanos = System.nanoTime();

            futures[i] = journal.append(CommandType.TRANSFER, opId, payload)
                    .thenAccept(v -> latenciesNanos[index] = System.nanoTime() - submitNanos);
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        long totalElapsedNanos = System.nanoTime() - startTime;
        executor.shutdown();

        Arrays.sort(latenciesNanos);

        double totalSec = totalElapsedNanos / 1_000_000_000.0;
        double opsPerSec = totalOps / totalSec;
        double p50Ms = latenciesNanos[(int) (totalOps * 0.50)] / 1_000_000.0;
        double p95Ms = latenciesNanos[(int) (totalOps * 0.95)] / 1_000_000.0;
        double p99Ms = latenciesNanos[(int) (totalOps * 0.99)] / 1_000_000.0;

        System.out.printf("Journal Benchmark: %d ops completed in %.3fs (%.1f ops/sec)%n", totalOps, totalSec, opsPerSec);
        System.out.printf("Latencies: P50=%.2fms, P95=%.2fms, P99=%.2fms%n", p50Ms, p95Ms, p99Ms);

        assertThat(totalOps).isEqualTo(5_000);
        assertThat(p99Ms).isLessThan(100.0); // P99 fsync batch latency well bounded
    }
}
