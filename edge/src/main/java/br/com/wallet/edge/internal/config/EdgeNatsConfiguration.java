package br.com.wallet.edge.internal.config;

import br.com.wallet.edge.internal.ingress.ConditionalOnEdgeIngress;
import br.com.wallet.edge.internal.recovery.JournalRecoveryWorker;
import br.com.wallet.edge.internal.resilience.BrokerCircuitBreaker;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

/**
 * Standalone NATS configuration for Edge Gateway independent runtime (TASK-PRC-3.2, REQ-PRC-006).
 */
@Configuration
@ConditionalOnEdgeIngress
public class EdgeNatsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EdgeNatsConfiguration.class);

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.token:}")
    private String natsToken;

    @Bean
    @ConditionalOnMissingBean(Connection.class)
    public Connection natsConnection(
            ObjectProvider<JournalRecoveryWorker> recoveryWorkerProvider,
            ObjectProvider<BrokerCircuitBreaker> circuitBreakerProvider
    ) throws IOException, InterruptedException {
        log.info("Edge connecting to standalone NATS server at: {}", natsUrl);
        Options.Builder builder = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(10))
                .connectionListener((conn, event) -> {
                    log.info("NATS Connection Event: {} (status: {})", event, conn.getStatus());
                    if (event == ConnectionListener.Events.RECONNECTED) {
                        log.info("NATS reconnected successfully! Triggering spool recovery scan (I-EDGE-004)...");
                        if (circuitBreakerProvider != null) {
                            circuitBreakerProvider.ifAvailable(BrokerCircuitBreaker::reset);
                        }
                        if (recoveryWorkerProvider != null) {
                            recoveryWorkerProvider.ifAvailable(worker ->
                                    Thread.ofVirtual().name("edge-recovery-reconnect").start(worker::runRecoveryScan)
                            );
                        }
                    }
                })
                .errorListener(new ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("NATS error occurred: {}", error);
                    }

                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        log.error("NATS exception occurred: {}", exp.getMessage(), exp);
                    }

                    @Override
                    public void slowConsumerDetected(Connection conn, Consumer consumer) {
                        log.warn("NATS slow consumer detected");
                    }
                });

        if (natsToken != null && !natsToken.isBlank()) {
            builder.token(natsToken.toCharArray());
        }

        try {
            Connection nc = Nats.connect(builder.build());
            log.info("Edge successfully connected to NATS server.");
            return nc;
        } catch (IOException | InterruptedException e) {
            log.warn("Edge failed to connect to NATS server at {}: {}. Spillover journal active.", natsUrl, e.getMessage());
            throw e;
        }
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
