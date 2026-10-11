package br.com.wallet.infrastructure.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.Objects;

public record ManagedRedisConnection(RedisClient client, StatefulRedisConnection<String, String> connection)
        implements AutoCloseable {

    public ManagedRedisConnection(
            final RedisClient client
    ) {
        this(client, Objects.requireNonNull(client, "client cannot be null").connect());
    }

    public ManagedRedisConnection(
            final RedisClient client,
            final StatefulRedisConnection<String, String> connection
    ) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.connection = Objects.requireNonNull(connection, "connection cannot be null");
    }

    @Override
    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }
}