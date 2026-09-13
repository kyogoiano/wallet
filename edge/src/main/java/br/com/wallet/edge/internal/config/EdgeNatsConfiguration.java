package br.com.wallet.edge.internal.config;

import br.com.wallet.edge.internal.ingress.ConditionalOnEdgeIngress;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public Connection natsConnection() throws IOException, InterruptedException {
        log.info("Edge connecting to standalone NATS server at: {}", natsUrl);
        Options.Builder builder = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(2))
                .maxReconnects(10)
                .reconnectWait(Duration.ofSeconds(1));

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
