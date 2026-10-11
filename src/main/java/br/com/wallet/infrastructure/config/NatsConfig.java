package br.com.wallet.infrastructure.config;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
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

    @Value("${nats.url:nats://nats:4222}")
    private String natsUrl;

    @Value("${nats.token:dfji348934jdd0i24uhjd29834ijrr0345jo0r3j034n}")
    private String natsToken;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        log.info("Connecting to NATS server at: {}", natsUrl);
        Options options = new Options.Builder()
                .server(natsUrl)
                .token(natsToken.toCharArray())
                .connectionTimeout(Duration.ofSeconds(2))
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(10))
                .connectionListener((conn, event) -> log.info("NATS Core Connection Event: {} (status: {})", event, conn.getStatus()))
                .errorListener(new ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("NATS Core error occurred: {}", error);
                    }

                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        log.error("NATS Core exception occurred: {}", exp.getMessage(), exp);
                    }

                    @Override
                    public void slowConsumerDetected(Connection conn, Consumer consumer) {
                        log.warn("NATS Core slow consumer detected");
                    }
                })
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