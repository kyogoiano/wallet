package br.com.wallet.integration.edge;

import br.com.wallet.edge.api.EdgeCommandResult;
import br.com.wallet.edge.internal.command.CommandAcceptanceService;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.ingress.EdgeRequestValidator;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.resilience.BrokerCircuitBreaker;
import br.com.wallet.edge.internal.resilience.IngressBulkhead;
import br.com.wallet.edge.internal.resilience.PerimeterRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EdgeCapacityIT: Spool Capacity & Saturation Integration Test (TASK-6.2, REQ-EDG-003, I-EDGE-005)")
class EdgeCapacityIT {

    private Path spoolDir;
    private SegmentedFileJournal journal;
    private CommandAcceptanceService acceptanceService;
    private BrokerCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() throws IOException {
        spoolDir = Files.createTempDirectory("edge-capacity-test-");

        // Set maximum capacity tiny (100KB) so that a 64KB segment quickly hits >= 95% saturation
        long segmentSize = 64 * 1024L;
        long maxCapacity = 64 * 1024L; // 1 segment fills 100%

        journal = new SegmentedFileJournal(spoolDir, segmentSize, maxCapacity, 1, 1L);
        journal.start();

        // Broker starts OPEN (simulating outage, forcing degraded spooling)
        circuitBreaker = new BrokerCircuitBreaker();
        circuitBreaker.tripForTest();

        EdgeCommandPublisher publisher = command -> {
            CompletableFuture<Void> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("Broker down"));
            return f;
        };

        acceptanceService = new CommandAcceptanceService(
                publisher,
                journal,
                new PerimeterRateLimiter(1000, 1000),
                new IngressBulkhead(1000),
                circuitBreaker,
                new EdgeRequestValidator()
        );
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
    @DisplayName("Should reject degraded acceptance with Saturated (HTTP 503 Retry-After: 5) when spool is saturated (I-EDGE-005)")
    void shouldReturnSaturatedWhenSpoolFull() throws Exception {
        // Because maxCapacity == segmentSize (64KB), usage reaches 100% as soon as segment is preallocated
        assertThat(journal.spoolUsagePercent()).isGreaterThanOrEqualTo(95.0);

        CommandEnvelope cmd = CommandEnvelope.create(UUID.randomUUID(), CommandType.TRANSFER, "{\"amount\": 10.00}", "10.0.0.1");

        EdgeCommandResult result = acceptanceService.acceptCommand(cmd).get(2, TimeUnit.SECONDS);

        assertThat(result).isInstanceOf(EdgeCommandResult.Saturated.class);
        EdgeCommandResult.Saturated saturated = (EdgeCommandResult.Saturated) result;
        assertThat(saturated.retryAfterSeconds()).isEqualTo(5);
        assertThat(saturated.reason()).contains("SATURATED");
    }
}
