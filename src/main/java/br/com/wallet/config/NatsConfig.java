package br.com.wallet.config;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Configuration
public class NatsConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        log.info("Connecting to NATS server at: {}", natsUrl);
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofSeconds(5))
                .maxReconnects(10)
                .reconnectWait(Duration.ofSeconds(1))
                .build();
        try {
            Connection nc = Nats.connect(options); // failing on tests
            log.info("Successfully connected to NATS server.");
            return nc;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to connect to NATS server: {}", e.getMessage());
            throw e;
        }
    }
}