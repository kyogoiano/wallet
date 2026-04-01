package br.com.wallet.config;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamConfiguration;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class NatsJetStreamBootstrap implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(NatsJetStreamBootstrap.class);

    private final Connection connection;

    public NatsJetStreamBootstrap(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        var jsm = connection.jetStreamManagement();
        log.info(">>>> Inicializando Streams NATS...");
        createStream(jsm, "commands", List.of("commands.*"));
        createStream(jsm, "events", List.of("events.*"));
    }

    private void createStream(@NonNull JetStreamManagement jsm,
                              @NonNull String name,
                              @NonNull List<String> subjects) throws Exception {
        try {
            jsm.getStreamInfo(name);
            log.info("Stream '{}' already exists!.", name);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10059 || e.getErrorCode() == 404) { // 10059 é o código NATS para "not found"
                jsm.addStream(StreamConfiguration.builder()
                        .name(name)
                        .subjects(subjects)
                        .build());
                log.info("Stream '{}' created successfully!", name);
            } else {
                throw e;
            }
        }
    }
}
