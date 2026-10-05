package br.com.wallet.edge.internal;

import br.com.wallet.edge.api.DurableOperationStateProvider;
import br.com.wallet.edge.api.OperationAuthorizationProvider;
import br.com.wallet.edge.internal.command.CommandAcceptanceService;
import br.com.wallet.edge.internal.idempotency.EdgeIdempotencyGate;
import br.com.wallet.edge.internal.ingress.EdgeRequestValidator;
import br.com.wallet.edge.internal.ingress.OperationStatusAuthorizationFilter;
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
import br.com.wallet.edge.internal.ingress.ConditionalOnEdgeIngress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import br.com.wallet.edge.api.CredentialResolver;
import br.com.wallet.edge.internal.security.HmacAuthenticationFilter;
import br.com.wallet.edge.internal.security.HmacSignatureVerifier;
import br.com.wallet.edge.internal.security.InMemoryCredentialResolver;
import br.com.wallet.edge.internal.security.replay.DragonflyNonceTracker;
import br.com.wallet.edge.internal.security.replay.InMemoryNonceTracker;
import br.com.wallet.security.replay.NonceTracker;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Spring configuration registering Edge Gateway components and lifecycle beans.
 */
@Configuration
@ConditionalOnEdgeIngress
public class EdgeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfiguration.class);

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
    public DurableOperationStateProvider durableOperationStateProvider() {
        return operationId -> Optional.empty();
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
            @Value("${edge.spool.directory:#{null}}") String spoolPath,
            @Value("${edge.spool.segment-size-bytes:67108864}") long segmentSize,
            @Value("${edge.spool.max-capacity-bytes:10737418240}") long maxCapacity
    ) throws IOException {
        Path path = (spoolPath != null && !spoolPath.isBlank())
                ? Path.of(spoolPath)
                : Path.of(System.getProperty("java.io.tmpdir"), "wallet-spool-" + java.util.UUID.randomUUID());
        return new SegmentedFileJournal(path, segmentSize, maxCapacity, 100, 1L);
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
    public EdgeIdempotencyGate edgeIdempotencyGate() {
        return new EdgeIdempotencyGate();
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
    public OperationAuthorizationProvider operationAuthorizationProvider() {
        return (operationId, tenantId) -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationStatusAuthorizationFilter operationStatusAuthorizationFilter(
            OperationAuthorizationProvider authorizationProvider
    ) {
        return new OperationStatusAuthorizationFilter(authorizationProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public CredentialResolver credentialResolver() {
        return new InMemoryCredentialResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public HmacSignatureVerifier hmacSignatureVerifier() {
        return new HmacSignatureVerifier();
    }

    @Bean
    @ConditionalOnMissingBean
    public NonceTracker nonceTracker(
            @Autowired(required = false) StatefulRedisConnection<String, String> existingConnection
    ) {
        if (existingConnection != null) {
            log.info("Initialized DragonflyNonceTracker using existing StatefulRedisConnection");
            return new DragonflyNonceTracker(existingConnection.sync());
        }

        log.warn("DragonflyDB Managed connection not available. Falling back to InMemoryNonceTracker");
        return new InMemoryNonceTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public HmacAuthenticationFilter hmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier,
            @Autowired(required = false) NonceTracker nonceTracker,
            @Autowired(required = false) Clock clock
    ) {
        return new HmacAuthenticationFilter(credentialResolver, signatureVerifier, nonceTracker, clock);
    }
}

