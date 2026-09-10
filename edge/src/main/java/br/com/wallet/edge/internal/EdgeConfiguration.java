package br.com.wallet.edge.internal;

import br.com.wallet.edge.internal.command.CommandAcceptanceService;
import br.com.wallet.edge.internal.ingress.EdgeRequestValidator;
import br.com.wallet.edge.internal.ingress.OperationStatusHub;
import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.api.EdgeCommandPublisher;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import br.com.wallet.edge.internal.recovery.JournalRecoveryWorker;
import br.com.wallet.edge.internal.recovery.SpoolAckTracker;
import br.com.wallet.edge.internal.resilience.BrokerCircuitBreaker;
import br.com.wallet.edge.internal.resilience.IngressBulkhead;
import br.com.wallet.edge.internal.resilience.PerimeterRateLimiter;
import br.com.wallet.edge.internal.transport.AltSvcWebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Spring configuration registering Edge Gateway components and lifecycle beans.
 */
@Configuration
public class EdgeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EdgeRequestValidator edgeRequestValidator(
            @Value("${edge.envelope.max-size-bytes:65536}") int maxEnvelopeSize
    ) {
        return new EdgeRequestValidator(maxEnvelopeSize);
    }

    @Bean
    @ConditionalOnMissingBean
    public PerimeterRateLimiter perimeterRateLimiter(
            @Value("${edge.rate-limit.capacity:1000}") long capacity,
            @Value("${edge.rate-limit.refill-rate:500}") long refillRate
    ) {
        return new PerimeterRateLimiter(capacity, refillRate);
    }

    @Bean
    @ConditionalOnMissingBean
    public IngressBulkhead ingressBulkhead(
            @Value("${edge.bulkhead.max-inflight:2048}") int maxInflight
    ) {
        return new IngressBulkhead(maxInflight);
    }

    @Bean
    @ConditionalOnMissingBean
    public BrokerCircuitBreaker brokerCircuitBreaker() {
        return new BrokerCircuitBreaker();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationStatusHub operationStatusHub() {
        return new OperationStatusHub();
    }

    @Bean
    @ConditionalOnMissingBean
    public br.com.wallet.edge.api.DurableOperationStateProvider durableOperationStateProvider() {
        return operationId -> java.util.Optional.empty();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpoolAckTracker spoolAckTracker() {
        return new SpoolAckTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public EdgeReadinessHealthIndicator edgeReadinessHealthIndicator() {
        return new EdgeReadinessHealthIndicator();
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public SegmentedFileJournal segmentedFileJournal(
            @Value("${edge.spool.directory:#{systemProperties['java.io.tmpdir']}/wallet-spool}") String spoolPath,
            @Value("${edge.spool.segment-size-bytes:67108864}") long segmentSize,
            @Value("${edge.spool.max-capacity-bytes:10737418240}") long maxCapacity
    ) throws IOException {
        return new SegmentedFileJournal(Path.of(spoolPath), segmentSize, maxCapacity, 100, 1L);
    }

    @Bean
    @ConditionalOnMissingBean
    public EdgeCommandPublisher edgeCommandPublisher() {
        // Fallback: When broker publisher is absent, return a failed future so
        // CommandAcceptanceService safely triggers degraded journal spillover (I-EDGE-001)
        return command -> CompletableFuture.failedFuture(
                new IllegalStateException("No broker EdgeCommandPublisher available; falling back to durable spool journal")
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public br.com.wallet.edge.internal.idempotency.EdgeIdempotencyGate edgeIdempotencyGate() {
        return new br.com.wallet.edge.internal.idempotency.EdgeIdempotencyGate();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandAcceptanceService commandAcceptanceService(
            EdgeCommandPublisher publisher,
            SegmentedFileJournal journal,
            PerimeterRateLimiter rateLimiter,
            IngressBulkhead bulkhead,
            BrokerCircuitBreaker circuitBreaker,
            EdgeRequestValidator validator,
            br.com.wallet.edge.internal.idempotency.EdgeIdempotencyGate idempotencyGate
    ) {
        return new CommandAcceptanceService(publisher, journal, rateLimiter, bulkhead, circuitBreaker, validator, idempotencyGate);
    }

    @Bean
    @ConditionalOnMissingBean
    public JournalRecoveryWorker journalRecoveryWorker(
            SegmentedFileJournal journal,
            EdgeCommandPublisher publisher,
            SpoolAckTracker ackTracker,
            EdgeReadinessHealthIndicator healthIndicator
    ) {
        return new JournalRecoveryWorker(journal, publisher, ackTracker, healthIndicator);
    }

    @Bean
    @ConditionalOnMissingBean
    public AltSvcWebFilter altSvcWebFilter() {
        return new AltSvcWebFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public br.com.wallet.edge.api.OperationAuthorizationProvider operationAuthorizationProvider() {
        return (operationId, tenantId) -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    public br.com.wallet.edge.internal.ingress.OperationStatusAuthorizationFilter operationStatusAuthorizationFilter(
            br.com.wallet.edge.api.OperationAuthorizationProvider authorizationProvider
    ) {
        return new br.com.wallet.edge.internal.ingress.OperationStatusAuthorizationFilter(authorizationProvider);
    }
}
