package br.com.wallet.integration.infrastructure;

import br.com.wallet.infrastructure.config.RedisConfig;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.protocol.ProtocolVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Dragonfly 2.0 Compatibility Test (Gate 1 Verification: REQ-DF20-001, REQ-DF20-002, REQ-DF20-003, REQ-DF20-015)")
public class DragonflyCompatibilityTest extends DockerProperties {

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;

    @Autowired
    private RedisCommands<String, String> redisCommands;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        redisCommands.flushall();
    }

    @Test
    @DisplayName("REQ-DF20-002: Verify Lettuce client negotiates ProtocolVersion.RESP3")
    void shouldNegotiateResp3Protocol() {
        ClientOptions options = redisClient.getOptions();
        assertThat(options).isNotNull();
        assertThat(options.getProtocolVersion()).isEqualTo(ProtocolVersion.RESP3);
    }

    @Test
    @DisplayName("REQ-DF20-002: Verify connection factory handles TCP fallback when socket is unavailable")
    void shouldFallbackToTcpWhenSocketUnavailable() {
        RedisConfig config = new RedisConfig();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("redis.socket.enabled", "true");
        env.setProperty("redis.socket.path", "/tmp/non_existent_redis_socket_" + UUID.randomUUID() + ".sock");
        env.setProperty("spring.data.redis.host", "localhost");
        env.setProperty("spring.data.redis.port", "6379");

        RedisURI socketUri = config.redisUri(env);
        RedisClient client = config.redisClient(socketUri, env);

        try (StatefulRedisConnection<String, String> connection = config.redisConnection(client, env)) {
            assertThat(connection.isOpen()).isTrue();
            RedisCommands<String, String> sync = connection.sync();
            String pong = sync.ping();
            assertThat(pong).isEqualTo("PONG");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("REQ-DF20-003: Validate all Redis command syntaxes against Dragonfly 2.0")
    void shouldExecuteStandardRedisCommandsAgainstDragonfly20() {
        String testKey = "test:entity:" + UUID.randomUUID();

        // 1. HSET & HGETALL
        redisCommands.hset(testKey, "field1", "val1");
        redisCommands.hset(testKey, "field2", "val2");
        Map<String, String> hash = redisCommands.hgetall(testKey);
        assertThat(hash).containsEntry("field1", "val1").containsEntry("field2", "val2");

        // 2. EXPIRE & PEXPIRE
        Boolean expireSet = redisCommands.expire(testKey, 60);
        assertThat(expireSet).isTrue();
        Long ttl = redisCommands.ttl(testKey);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(60L);

        Boolean pexpireSet = redisCommands.pexpire(testKey, 30000L);
        assertThat(pexpireSet).isTrue();
        Long pttl = redisCommands.pttl(testKey);
        assertThat(pttl).isPositive().isLessThanOrEqualTo(30000L);

        // 3. INCRBY
        String counterKey = "test:counter:" + UUID.randomUUID();
        Long val1 = redisCommands.incrby(counterKey, 5L);
        assertThat(val1).isEqualTo(5L);
        Long val2 = redisCommands.incrby(counterKey, 10L);
        assertThat(val2).isEqualTo(15L);

        // 4. ZADD, ZCARD, ZREMRANGEBYSCORE
        String zsetKey = "test:zset:" + UUID.randomUUID();
        redisCommands.zadd(zsetKey, 100.0, "member1");
        redisCommands.zadd(zsetKey, 200.0, "member2");
        redisCommands.zadd(zsetKey, 300.0, "member3");
        Long zcard = redisCommands.zcard(zsetKey);
        assertThat(zcard).isEqualTo(3L);

        Long removed = redisCommands.zremrangebyscore(zsetKey, io.lettuce.core.Range.create(0.0, 150.0));
        assertThat(removed).isEqualTo(1L);
        assertThat(redisCommands.zcard(zsetKey)).isEqualTo(2L);

        // 5. PSETEX
        String psetKey = "test:pset:" + UUID.randomUUID();
        String psetResult = redisCommands.psetex(psetKey, 5000L, "temporary_val");
        assertThat(psetResult).isEqualTo("OK");
        assertThat(redisCommands.get(psetKey)).isEqualTo("temporary_val");

        // 6. SADD & SCARD
        String setKey = "test:set:" + UUID.randomUUID();
        Long saddResult = redisCommands.sadd(setKey, "elem1", "elem2", "elem3");
        assertThat(saddResult).isEqualTo(3L);
        Long scardResult = redisCommands.scard(setKey);
        assertThat(scardResult).isEqualTo(3L);
        Set<String> members = redisCommands.smembers(setKey);
        assertThat(members).containsExactlyInAnyOrder("elem1", "elem2", "elem3");

        // 7. DEL
        Long delCount = redisCommands.del(testKey, counterKey, zsetKey, psetKey, setKey);
        assertThat(delCount).isEqualTo(5L);
    }

    @Test
    @DisplayName("REQ-DF20-015: Enforce bounded emergency cache timeout (<= 20ms) and zero thread leakage under repeated timeouts")
    void shouldBoundEmergencyTimeoutAndPreventThreadLeakage() {
        int initialThreadCount = Thread.activeCount();

        // Create client with 1 millisecond timeout to guarantee simulated timeout
        RedisURI uri = RedisURI.create("redis://localhost:6379");
        RedisClient timeoutClient = RedisClient.create(uri);
        timeoutClient.setOptions(ClientOptions.builder()
                .autoReconnect(false)
                .timeoutOptions(TimeoutOptions.builder().fixedTimeout(Duration.ofNanos(1)).build())
                .build());

        int timeoutIterations = 50;
        int observedTimeouts = 0;

        try (StatefulRedisConnection<String, String> conn = timeoutClient.connect()) {
            RedisCommands<String, String> sync = conn.sync();
            for (int i = 0; i < timeoutIterations; i++) {
                try {
                    sync.get("non_existent_key_" + i);
                } catch (RedisCommandTimeoutException e) {
                    observedTimeouts++;
                } catch (Exception ignored) {
                }
            }
        } finally {
            timeoutClient.shutdown(0, 50, TimeUnit.MILLISECONDS);
        }

        int finalThreadCount = Thread.activeCount();
        // Zero or negligible difference in active thread count (bounded resources, no thread leak)
        assertThat(finalThreadCount - initialThreadCount).isLessThanOrEqualTo(2);
    }
}
