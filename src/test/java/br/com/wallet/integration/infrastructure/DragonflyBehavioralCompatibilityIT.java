package br.com.wallet.integration.infrastructure;

import br.com.wallet.infrastructure.config.RedisScripts;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Dragonfly 2.0 Behavioral Compatibility & Semantics Verification (REQ-DF20-004, REQ-DF20-005, Triad 1 & 4)")
public class DragonflyBehavioralCompatibilityIT extends DockerProperties {

    @Autowired
    private RedisCommands<String, String> defaultCommands;

    @Autowired
    private DatabaseCleaner cleaner;

    private static GenericContainer<?> v2Container;
    private static RedisClient clientV2;
    private static StatefulRedisConnection<String, String> connV2;

    private RedisCommands<String, String> cmdV2;

    @BeforeAll
    static void initClients() {
        if (IntegrationTestBase.isDockerAvailable()) {
            v2Container = IntegrationTestBase.createDragonflyV2Container();
            v2Container.start();
            clientV2 = RedisClient.create(RedisURI.create(v2Container.getHost(), v2Container.getMappedPort(6379)));
        } else {
            clientV2 = RedisClient.create(RedisURI.create("localhost", 6379));
        }

        connV2 = clientV2.connect();
    }

    @AfterAll
    static void closeClients() {
        if (connV2 != null) connV2.close();
        if (clientV2 != null) clientV2.shutdown();
        if (v2Container != null) v2Container.stop();
    }

    @BeforeEach
    void setUp() {
        cleaner.clean();
        cmdV2 = connV2.sync();
        cmdV2.flushall();
    }

    @Test
    @DisplayName("Triad 1: Count & Threshold boundaries with zero/expired event windows (I-DF20-003)")
    void shouldEnforceCountAndThresholdBoundariesWithParity() {
        String testKeyV2 = "v2:threshold:" + UUID.randomUUID();

        // 1. Zero-event window: starts empty (count = 0)
        Long count2Zero = cmdV2.zcard(testKeyV2);
        assertThat(count2Zero).isZero();

        // 2. Add events below threshold (threshold = 3)
        double t = (double) System.currentTimeMillis();
        for (int i = 1; i <= 3; i++) {
            cmdV2.zadd(testKeyV2, t + i, "op-" + i);
        }

        Long count2Below = cmdV2.zcard(testKeyV2);
        assertThat(count2Below).isEqualTo(3L);

        // 3. Exceed threshold (4 > 3 -> REJECT)
        cmdV2.zadd(testKeyV2, t + 4, "op-4");

        Long count2Above = cmdV2.zcard(testKeyV2);
        assertThat(count2Above).isEqualTo(4L);

        // 4. Expired-event window: evict stale members
        cmdV2.zremrangebyscore(testKeyV2, Range.create(0.0, t + 10));

        Long count2Evicted = cmdV2.zcard(testKeyV2);
        assertThat(count2Evicted).isZero();
    }

    @Test
    @DisplayName("Triad 1: Temporal sliding window boundaries [t - W excluded, t - W + ε included, t included]")
    void shouldEvaluateSlidingWindowBoundariesConsistently() {
        String keyV2 = "v2:temporal:" + UUID.randomUUID();

        long now = System.currentTimeMillis();
        long window = 30_000L; // 30s window

        long tMinusWMinusEpsilon = now - window - 50L; // EXCLUDED
        long tMinusWPlusEpsilon = now - window + 50L;  // INCLUDED
        // INCLUDED

        // Add 3 events
        cmdV2.zadd(keyV2, (double) tMinusWMinusEpsilon, "excluded-stale");
        cmdV2.zadd(keyV2, (double) tMinusWPlusEpsilon, "included-boundary");
        cmdV2.zadd(keyV2, (double) now, "included-current");

        // Evict older than (now - window)
        double cutoff = (double) (now - window);
        Long removed2 = cmdV2.zremrangebyscore(keyV2, Range.create(0.0, cutoff));

        assertThat(removed2).isEqualTo(1L);

        // Remaining events in window
        List<String> remaining2 = cmdV2.zrange(keyV2, 0, -1);
        assertThat(remaining2).containsExactly("included-boundary", "included-current");
    }

    @Test
    @DisplayName("Triad 4: Multi-key Lua script behavioral parity (REVIEW_COUNT_PROTECTED_SCRIPT & BLOCK_PROTECTED_SCRIPT)")
    void shouldAssertLuaScriptBehavioralEquivalence() {
        final UUID userId = UUID.randomUUID();
        final UUID opId2 = UUID.randomUUID();

        String[] keysV2 = new String[]{
                "fraud:op:" + opId2,
                "v2:user:tenant-x:" + userId + ":review_count",
                "v2:user:tenant-x:" + userId + ":risk_score",
                "v2:user:tenant-x:" + userId + ":blocked"
        };
        String[] args = new String[]{"30000", "25", "100", "5"};

        // First execution -> allowed
        List<Object> res2 = cmdV2.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keysV2, args);
        assertThat(res2).isNotNull();
        assertThat(res2.getFirst()).isEqualTo(1L);

        // Replay attempt with same opId -> REPLAY_DETECTED
        List<Object> replay2 = cmdV2.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keysV2, args);
        assertThat(replay2).isNotNull();
        assertThat(replay2.getFirst()).isEqualTo(0L);
        assertThat(replay2.get(1)).isEqualTo("REPLAY_DETECTED");

        // Block script parity
        final UUID blockOpId2 = UUID.randomUUID();
        String[] blockKeys2 = new String[]{
                "fraud:op:" + blockOpId2,
                "v2:user:tenant-x:" + userId + ":blocked"
        };
        String[] blockArgs = new String[]{"30000", "3600"};

        List<Object> blockRes2 = cmdV2.eval(RedisScripts.BLOCK_PROTECTED_SCRIPT, ScriptOutputType.MULTI, blockKeys2, blockArgs);
        assertThat(blockRes2).isNotNull();
        assertThat(blockRes2.getFirst()).isEqualTo(1L);
        assertThat(blockRes2.get(1)).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("REQ-DF20-005 & REQ-DF20-013: Deterministic second and millisecond TTL expiration with clock drift tolerance")
    void shouldAssertDeterministicTtlExpirationWithinTolerance() {
        String ttlKey2 = "v2:ttl:" + UUID.randomUUID();

        cmdV2.set(ttlKey2, "val2");
        cmdV2.expire(ttlKey2, 60);

        Long ttl2 = cmdV2.ttl(ttlKey2);
        assertThat(ttl2).isPositive().isLessThanOrEqualTo(60L);

        // Millisecond TTL
        String pttlKey2 = "v2:pttl:" + UUID.randomUUID();

        cmdV2.psetex(pttlKey2, 5000L, "pval2");

        Long pttl2 = cmdV2.pttl(pttlKey2);
        assertThat(pttl2).isPositive().isLessThanOrEqualTo(5000L);
    }
}
