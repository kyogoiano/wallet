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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;

@Configuration
public class RedisConfig {


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
    public StatefulRedisConnection<String, String> redisConnection(RedisClient client) {
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