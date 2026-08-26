package br.com.wallet.infrastructure.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.resource.DefaultClientResources;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisURI redisUri(@NonNull Environment env) {
        boolean useSocket = env.getProperty("redis.socket.enabled", Boolean.class, false);

        if (useSocket) {
            String socketPath = env.getProperty("redis.socket.path", "/var/run/redis/redis.sock");
            return RedisURI.Builder.socket(socketPath).build();
        }

        String host = env.getProperty("spring.data.redis.host", "localhost");
        int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);

        return RedisURI.create("redis://" + host + ":" + port);
    }

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(@Autowired @NonNull RedisURI redisUri, @NonNull Environment env) {
        boolean useSocket = env.getProperty("redis.socket.enabled", Boolean.class, false);

        final var resources = DefaultClientResources.builder()
                .ioThreadPoolSize(4)
                .computationThreadPoolSize(4)
                .build();
        final var client = RedisClient.create(
                resources, redisUri
        );

        final var socketOptionsBuilder = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(2));

        if (useSocket) {
            socketOptionsBuilder.keepAlive(SocketOptions.KeepAliveOptions.builder().enable(false).build());
        }

        client.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .pingBeforeActivateConnection(true)
                .protocolVersion(ProtocolVersion.RESP3)
                .replayFilter(cmd -> false)
                .socketOptions(socketOptionsBuilder.build())
                .timeoutOptions(TimeoutOptions.builder().fixedTimeout(Duration.ofSeconds(2)).build())
                .build());

        return client;
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> redisConnection(RedisClient client, @NonNull Environment env) {
        boolean useSocket = env.getProperty("redis.socket.enabled", Boolean.class, false);
        String socketPath = env.getProperty("redis.socket.path", "/var/run/redis/redis.sock");

        if (useSocket) {
            // Attempt UDS connection with retry in case the socket file is being initialized by Dragonfly
            for (int attempt = 1; attempt <= 5; attempt++) {
                try {
                    log.info("Connecting to DragonflyDB via Unix Domain Socket: {} (attempt {}/5)", socketPath, attempt);
                    return client.connect();
                } catch (Exception e) {
                    if (attempt < 5) {
                        log.warn("UDS connection attempt {} failed: {}. Retrying in 500ms...", attempt, e.getMessage());
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        // Fallback to TCP if socket is still unavailable
                        String host = env.getProperty("spring.data.redis.host", "localhost");
                        int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);
                        log.warn("Failed to connect to Unix Domain Socket after 5 attempts. Falling back to TCP at {}:{}", host, port);
                        RedisURI tcpUri = RedisURI.create("redis://" + host + ":" + port);
                        RedisClient tcpClient = RedisClient.create(tcpUri);
                        return tcpClient.connect();
                    }
                }
            }
        }

        return client.connect();
    }

    @Bean
    public RedisCommands<String, String> redisCommands(StatefulRedisConnection<String, String> connection) {
        return connection.sync();
    }

    @Bean
    public RedisAsyncCommands<String, String> redisAsyncCommands(StatefulRedisConnection<String, String> connection) {
        return connection.async();
    }
}