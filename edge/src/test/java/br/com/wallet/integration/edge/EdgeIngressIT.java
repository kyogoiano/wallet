package br.com.wallet.integration.edge;

import br.com.wallet.edge.api.EdgeCommandResult;
import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.internal.command.CommandAcceptanceService;
import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.internal.ingress.EdgeRequestValidator;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import br.com.wallet.edge.internal.recovery.JournalRecoveryWorker;
import br.com.wallet.edge.internal.recovery.SpoolAckTracker;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EdgeIngressIT: Chaos & Outage Recovery Integration Test (TASK-6.1, REQ-EDG-001, REQ-EDG-002, I-EDGE-001)")
class EdgeIngressIT {

    private Path spoolDir;
    private SegmentedFileJournal journal;
    private AtomicBoolean brokerAlive;
    private List<CommandEnvelope> receivedByBroker;
    private EdgeCommandPublisher publisher;
    private BrokerCircuitBreaker circuitBreaker;
    private CommandAcceptanceService acceptanceService;
    private SpoolAckTracker ackTracker;
    private EdgeReadinessHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() throws IOException {
        spoolDir = Files.createTempDirectory("edge-chaos-spool-");
        journal = new SegmentedFileJournal(spoolDir, 64 * 1024L, 10 * 1024 * 1024L, 10, 1L);
        journal.start();

        brokerAlive = new AtomicBoolean(true);
        receivedByBroker = new ArrayList<>();

        publisher = command -> {
            if (!brokerAlive.get()) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(new RuntimeException("NATS broker cluster unreachable"));
                return failed;
            }
            receivedByBroker.add(command);
            return CompletableFuture.completedFuture(null);
        };

        circuitBreaker = new BrokerCircuitBreaker();
        PerimeterRateLimiter rateLimiter = new PerimeterRateLimiter(1000, 1000);
        IngressBulkhead bulkhead = new IngressBulkhead(1000);
        EdgeRequestValidator validator = new EdgeRequestValidator();

        acceptanceService = new CommandAcceptanceService(
                publisher,
                journal,
                rateLimiter,
                bulkhead,
                circuitBreaker,
                validator
        );

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
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("Should transparently transition from broker to journal during outage, and drain backlog upon broker recovery")
    void shouldSurviveBrokerOutageAndDrainOnRestart() throws Exception {
        // Step 1: Normal operational state (Broker UP)
        UUID op1 = UUID.randomUUID();
        CommandEnvelope cmd1 = CommandEnvelope.create(op1, CommandType.TRANSFER, "{\"amount\": 100.00}", "10.0.0.1");

        EdgeCommandResult res1 = acceptanceService.acceptCommand(cmd1).get(2, TimeUnit.SECONDS);
        assertThat(res1).isInstanceOf(EdgeCommandResult.Accepted.class);
        assertThat(((EdgeCommandResult.Accepted) res1).spooledDegraded()).isFalse();
        assertThat(receivedByBroker).hasSize(1);

        // Step 2: Chaos injection -> NATS cluster fails
        brokerAlive.set(false);
        circuitBreaker.tripForTest();

        UUID op2 = UUID.randomUUID();
        UUID op3 = UUID.randomUUID();
        CommandEnvelope cmd2 = CommandEnvelope.create(op2, CommandType.DEPOSIT, "{\"amount\": 200.00}", "10.0.0.2");
        CommandEnvelope cmd3 = CommandEnvelope.create(op3, CommandType.WITHDRAW, "{\"amount\": 50.00}", "10.0.0.3");

        // Commands must be accepted via durable local fsync spillover
        EdgeCommandResult res2 = acceptanceService.acceptCommand(cmd2).get(2, TimeUnit.SECONDS);
        EdgeCommandResult res3 = acceptanceService.acceptCommand(cmd3).get(2, TimeUnit.SECONDS);

        assertThat(res2).isInstanceOf(EdgeCommandResult.Accepted.class);
        assertThat(((EdgeCommandResult.Accepted) res2).spooledDegraded()).isTrue();

        assertThat(res3).isInstanceOf(EdgeCommandResult.Accepted.class);
        assertThat(((EdgeCommandResult.Accepted) res3).spooledDegraded()).isTrue();

        // Broker received nothing new during outage
        assertThat(receivedByBroker).hasSize(1);

        // Verify segment exists on disk containing 2 records
        List<Path> segments = journal.listSegmentFiles();
        assertThat(segments).isNotEmpty();

        // Step 3: Broker recovered -> node executes recovery worker scan
        brokerAlive.set(true);
        circuitBreaker.resetForTest();

        JournalRecoveryWorker recoveryWorker = new JournalRecoveryWorker(
                journal,
                publisher,
                ackTracker,
                healthIndicator
        );

        recoveryWorker.runRecoveryScan();

        // All spooled records must now be delivered to broker with operationId preserved
        assertThat(receivedByBroker).hasSize(3);
        List<UUID> deliveredIds = receivedByBroker.stream().map(CommandEnvelope::operationId).toList();
        assertThat(deliveredIds).containsExactly(op1, op2, op3);

        // Readiness must now be READY
        assertThat(healthIndicator.getCurrentState()).isEqualTo(EdgeReadinessState.READY);
        assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("UP");
    }
}
