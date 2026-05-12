package br.com.wallet.config;

import br.com.wallet.infrasctructure.messaging.publisher.JetStreamConfig;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class NatsJetStreamBootstrap implements InitializingBean, JetStreamConfig {
    private static final Logger log = LoggerFactory.getLogger(NatsJetStreamBootstrap.class);

    private final Connection connection;

    public NatsJetStreamBootstrap(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        var jsm = connection.jetStreamManagement();
        log.info(">>>> Inicializando Streams NATS...");
        ensureStream(jsm, "commands", "commands.*", Duration.ofHours(24));
        ensureStream(jsm, "commands_dlq", "commands.dlq.*", Duration.ofDays(7));
        ensureStream(jsm, "events", "events.*", Duration.ofDays(7));
    }
}
