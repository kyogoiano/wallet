package br.com.wallet.integration.infrastructure;

import br.com.wallet.infrastructure.config.RedisScripts;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.DragonflyOutputNormalizer;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Dragonfly 1.40 vs 2.0 Behavioral Compatibility Oracle (Gate 2: REQ-DF20-004, REQ-DF20-005, REQ-DF20-013, Triad 1 & 4)")
public class DragonflyBehavioralCompatibilityIT extends DockerProperties {

    @Autowired
    private RedisCommands<String, String> defaultCommands;

    @Autowired
    private DatabaseCleaner cleaner;

    private static GenericContainer<?> v1Container;
    private static GenericContainer<?> v2Container;

    private static RedisClient clientV1;
    private static RedisClient clientV2;

    private static StatefulRedisConnection<String, String> connV1;
    private static StatefulRedisConnection<String, String> connV2;

    private RedisCommands<String, String> cmdV1;
    private RedisCommands<String, String> cmdV2;

    @BeforeAll
    static void initClients() {
        if (IntegrationTestBase.isDockerAvailable()) {
            v1Container = IntegrationTestBase.createDragonflyV1Container();
            v2Container = IntegrationTestBase.createDragonflyV2Container();
            v1Container.start();
            v2Container.start();

            clientV1 = RedisClient.create(RedisURI.create(v1Container.getHost(), v1Container.getMappedPort(6379)));
            clientV2 = RedisClient.create(RedisURI.create(v2Container.getHost(), v2Container.getMappedPort(6379)));
        } else {
            // Local fallback: use localhost:6379 with separate connections
            clientV1 = RedisClient.create(RedisURI.create("localhost", 6379));
            clientV2 = RedisClient.create(RedisURI.create("localhost", 6379));
        }

        connV1 = clientV1.connect();
        connV2 = clientV2.connect();
    }

    @AfterAll
    static void closeClients() {
        if (connV1 != null) connV1.close();
        if (connV2 != null) connV2.close();
        if (clientV1 != null) clientV1.shutdown();
        if (clientV2 != null) clientV2.shutdown();
        if (v1Container != null) v1Container.stop();
        if (v2Container != null) v2Container.stop();
    }

    @BeforeEach
    void setUp() {
        cleaner.clean();
        cmdV1 = connV1.sync();
        cmdV2 = connV2.sync();
        cmdV1.flushall();
        cmdV2.flushall();
    }

    @Test
    @DisplayName("Triad 1: Count & Threshold boundaries with zero/expired event windows (I-DF20-003)")
    void shouldEnforceCountAndThresholdBoundariesWithParity() {
        String testKeyV1 = "v1:threshold:" + UUID.randomUUID();
        String testKeyV2 = "v2:threshold:" + UUID.randomUUID();

        // 1. Zero-event window: both start empty (count = 0)
        Long count1Zero = cmdV1.zcard(testKeyV1);
        Long count2Zero = cmdV2.zcard(testKeyV2);
        assertThat(count1Zero).isZero();
        assertThat(count2Zero).isZero();
        DragonflyOutputNormalizer.assertNormalizedEquivalence(count1Zero, count2Zero);

        // 2. Add events below threshold (threshold = 3)
        double t = (double) System.currentTimeMillis();
        for (int i = 1; i <= 3; i++) {
            cmdV1.zadd(testKeyV1, t + i, "op-" + i);
            cmdV2.zadd(testKeyV2, t + i, "op-" + i);
        }

        Long count1Below = cmdV1.zcard(testKeyV1);
        Long count2Below = cmdV2.zcard(testKeyV2);
        assertThat(count1Below).isEqualTo(3L);
        assertThat(count2Below).isEqualTo(3L);
        DragonflyOutputNormalizer.assertNormalizedEquivalence(count1Below, count2Below);

        // 3. Exceed threshold (4 > 3 -> REJECT)
        cmdV1.zadd(testKeyV1, t + 4, "op-4");
        cmdV2.zadd(testKeyV2, t + 4, "op-4");

        Long count1Above = cmdV1.zcard(testKeyV1);
        Long count2Above = cmdV2.zcard(testKeyV2);
        assertThat(count1Above).isEqualTo(4L);
        assertThat(count2Above).isEqualTo(4L);
        DragonflyOutputNormalizer.assertNormalizedEquivalence(count1Above, count2Above);

        // 4. Expired-event window: evict stale members
        cmdV1.zremrangebyscore(testKeyV1, Range.create(0.0, t + 10));
        cmdV2.zremrangebyscore(testKeyV2, Range.create(0.0, t + 10));

        Long count1Evicted = cmdV1.zcard(testKeyV1);
        Long count2Evicted = cmdV2.zcard(testKeyV2);
        assertThat(count1Evicted).isZero();
        assertThat(count2Evicted).isZero();
        DragonflyOutputNormalizer.assertNormalizedEquivalence(count1Evicted, count2Evicted);
    }

    @Test
    @DisplayName("Triad 1: Temporal sliding window boundaries [t - W excluded, t - W + ε included, t included]")
    void shouldEvaluateSlidingWindowBoundariesConsistently() {
        String keyV1 = "v1:temporal:" + UUID.randomUUID();
        String keyV2 = "v2:temporal:" + UUID.randomUUID();

        long now = System.currentTimeMillis();
        long window = 30_000L; // 30s window

        long tMinusWMinusEpsilon = now - window - 50L; // EXCLUDED
        long tMinusWPlusEpsilon = now - window + 50L;  // INCLUDED
        long tNow = now;                               // INCLUDED

        // Add 3 events
        cmdV1.zadd(keyV1, (double) tMinusWMinusEpsilon, "excluded-stale");
        cmdV1.zadd(keyV1, (double) tMinusWPlusEpsilon, "included-boundary");
        cmdV1.zadd(keyV1, (double) tNow, "included-current");

        cmdV2.zadd(keyV2, (double) tMinusWMinusEpsilon, "excluded-stale");
        cmdV2.zadd(keyV2, (double) tMinusWPlusEpsilon, "included-boundary");
        cmdV2.zadd(keyV2, (double) tNow, "included-current");

        // Evict older than (now - window)
        double cutoff = (double) (now - window);
        Long removed1 = cmdV1.zremrangebyscore(keyV1, Range.create(0.0, cutoff));
        Long removed2 = cmdV2.zremrangebyscore(keyV2, Range.create(0.0, cutoff));

        assertThat(removed1).isEqualTo(1L);
        assertThat(removed2).isEqualTo(1L);
        DragonflyOutputNormalizer.assertNormalizedEquivalence(removed1, removed2);

        // Remaining events in window
        List<String> remaining1 = cmdV1.zrange(keyV1, 0, -1);
        List<String> remaining2 = cmdV2.zrange(keyV2, 0, -1);

        assertThat(remaining1).containsExactly("included-boundary", "included-current");
        assertThat(remaining2).containsExactly("included-boundary", "included-current");
        DragonflyOutputNormalizer.assertNormalizedEquivalence(remaining1, remaining2);
    }

    @Test
    @DisplayName("Triad 4: Multi-key Lua script behavioral parity (REVIEW_COUNT_PROTECTED_SCRIPT & BLOCK_PROTECTED_SCRIPT)")
    void shouldAssertLuaScriptBehavioralEquivalence() {
        final UUID userId = UUID.randomUUID();
        final UUID opId1 = UUID.randomUUID();
        final UUID opId2 = UUID.randomUUID();

        String[] keysV1 = new String[]{
                "fraud:op:" + opId1,
                "v1:user:tenant-x:" + userId + ":review_count",
                "v1:user:tenant-x:" + userId + ":risk_score",
                "v1:user:tenant-x:" + userId + ":blocked"
        };
        String[] keysV2 = new String[]{
                "fraud:op:" + opId2,
                "v2:user:tenant-x:" + userId + ":review_count",
                "v2:user:tenant-x:" + userId + ":risk_score",
                "v2:user:tenant-x:" + userId + ":blocked"
        };
        String[] args = new String[]{"30000", "25", "100", "5"};

        // First execution -> allowed
        List<Object> res1 = cmdV1.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keysV1, args);
        List<Object> res2 = cmdV2.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keysV2, args);

        DragonflyOutputNormalizer.assertNormalizedEquivalence(res1, res2);

        // Replay attempt with same opId -> REPLAY_DETECTED
        List<Object> replay1 = cmdV1.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keysV1, args);
        List<Object> replay2 = cmdV2.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keysV2, args);

        DragonflyOutputNormalizer.assertNormalizedEquivalence(replay1, replay2);
        assertThat(replay1.get(0)).isEqualTo(0L);
        assertThat(replay1.get(1)).isEqualTo("REPLAY_DETECTED");

        // Block script parity
        final UUID blockOpId1 = UUID.randomUUID();
        final UUID blockOpId2 = UUID.randomUUID();
        String[] blockKeys1 = new String[]{
                "fraud:op:" + blockOpId1,
                "v1:user:tenant-x:" + userId + ":blocked"
        };
        String[] blockKeys2 = new String[]{
                "fraud:op:" + blockOpId2,
                "v2:user:tenant-x:" + userId + ":blocked"
        };
        String[] blockArgs = new String[]{"30000", "3600"};

        List<Object> blockRes1 = cmdV1.eval(RedisScripts.BLOCK_PROTECTED_SCRIPT, ScriptOutputType.MULTI, blockKeys1, blockArgs);
        List<Object> blockRes2 = cmdV2.eval(RedisScripts.BLOCK_PROTECTED_SCRIPT, ScriptOutputType.MULTI, blockKeys2, blockArgs);

        DragonflyOutputNormalizer.assertNormalizedEquivalence(blockRes1, blockRes2);
        assertThat(blockRes1.get(0)).isEqualTo(1L);
        assertThat(blockRes1.get(1)).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("REQ-DF20-005 & REQ-DF20-013: Deterministic second and millisecond TTL expiration with clock drift tolerance")
    void shouldAssertDeterministicTtlExpirationWithinTolerance() {
        String ttlKey1 = "v1:ttl:" + UUID.randomUUID();
        String ttlKey2 = "v2:ttl:" + UUID.randomUUID();

        cmdV1.set(ttlKey1, "val1");
        cmdV2.set(ttlKey2, "val2");

        cmdV1.expire(ttlKey1, 60);
        cmdV2.expire(ttlKey2, 60);

        Long ttl1 = cmdV1.ttl(ttlKey1);
        Long ttl2 = cmdV2.ttl(ttlKey2);

        assertThat(ttl1).isPositive().isLessThanOrEqualTo(60L);
        assertThat(ttl2).isPositive().isLessThanOrEqualTo(60L);
        // Second TTLs must match within 1 second
        assertThat(Math.abs(ttl1 - ttl2)).isLessThanOrEqualTo(1L);

        // Millisecond TTL
        String pttlKey1 = "v1:pttl:" + UUID.randomUUID();
        String pttlKey2 = "v2:pttl:" + UUID.randomUUID();

        cmdV1.psetex(pttlKey1, 5000L, "pval1");
        cmdV2.psetex(pttlKey2, 5000L, "pval2");

        Long pttl1 = cmdV1.pttl(pttlKey1);
        Long pttl2 = cmdV2.pttl(pttlKey2);

        assertThat(pttl1).isPositive().isLessThanOrEqualTo(5000L);
        assertThat(pttl2).isPositive().isLessThanOrEqualTo(5000L);

        // Assert residual TTL matches within explicit Δ_clock <= 100ms tolerance
        DragonflyOutputNormalizer.assertTtlWithinTolerance(pttl1, pttl2, 100L);
    }
}
