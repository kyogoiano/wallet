package br.com.wallet.integration.infrastructure;

import br.com.wallet.infrastructure.config.RedisScripts;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("DragonflyDB & Lua Script Compatibility Integration Tests (I-DF-001, I-DF-002, I-DF-004)")
public class DragonflyLuaCompatibilityIT extends DockerProperties {

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
    @DisplayName("Should execute REVIEW_COUNT_PROTECTED_SCRIPT and accumulate risk score atomically")
    void shouldExecuteReviewScriptAndAccumulateRisk() {
        final UUID userId = UUID.randomUUID();
        final UUID opId1 = UUID.randomUUID();

        String[] keys = new String[]{
                "fraud:op:" + opId1,
                "user:" + userId + ":review_count",
                "user:" + userId + ":risk_score",
                "user:" + userId + ":blocked"
        };
        String[] args = new String[]{"30000", "25", "100", "5"};

        List<Object> result1 = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );

        assertThat(result1).isNotNull().hasSize(4);
        assertThat(result1.get(0)).isEqualTo(1L); // Processed successfully
        assertThat(result1.get(1)).isEqualTo(1L); // Review count = 1
        assertThat(result1.get(2)).isEqualTo(25L); // Risk score = 25
        assertThat(result1.get(3)).isEqualTo(0L); // Not blocked yet
        assertThat(redisCommands.get("user:" + userId + ":blocked")).isNull();

        // Second operation with increment 30
        final UUID opId2 = UUID.randomUUID();
        keys[0] = "fraud:op:" + opId2;
        args[1] = "30";

        List<Object> result2 = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );

        assertThat(result2.get(0)).isEqualTo(1L);
        assertThat(result2.get(1)).isEqualTo(2L); // Review count = 2
        assertThat(result2.get(2)).isEqualTo(55L); // Risk score = 25 + 30 = 55
        assertThat(result2.get(3)).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should detect replay in REVIEW_COUNT_PROTECTED_SCRIPT (I-DF-004)")
    void shouldDetectReplayInReviewScript() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();

        String[] keys = new String[]{
                "fraud:op:" + opId,
                "user:" + userId + ":review_count",
                "user:" + userId + ":risk_score",
                "user:" + userId + ":blocked"
        };
        String[] args = new String[]{"30000", "20", "100", "5"};

        // First attempt -> Processed
        List<Object> result1 = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );
        assertThat(result1.getFirst()).isEqualTo(1L);

        // Replay attempt with same opId -> Replay detected
        List<Object> result2 = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );
        assertThat(result2.get(0)).isEqualTo(0L);
        assertThat(result2.get(1)).isEqualTo("REPLAY_DETECTED");

        // Review counter and risk score should not have been incremented a second time
        assertThat(redisCommands.get("user:" + userId + ":review_count")).isEqualTo("1");
        assertThat(redisCommands.get("user:" + userId + ":risk_score")).isEqualTo("20");
    }

    @Test
    @DisplayName("Should automatically block user when risk score threshold is breached")
    void shouldBlockUserWhenRiskScoreThresholdBreached() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();

        String[] keys = new String[]{
                "fraud:op:" + opId,
                "user:" + userId + ":review_count",
                "user:" + userId + ":risk_score",
                "user:" + userId + ":blocked"
        };
        // Risk increment 120 exceeds threshold 100
        String[] args = new String[]{"30000", "120", "100", "5"};

        List<Object> result = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );

        assertThat(result.get(0)).isEqualTo(1L);
        assertThat(result.get(3)).isEqualTo(1L); // Blocked = 1
        assertThat(redisCommands.get("user:" + userId + ":blocked")).isEqualTo("1");
    }

    @Test
    @DisplayName("Should automatically block user when review count threshold is breached")
    void shouldBlockUserWhenReviewCountThresholdBreached() {
        final UUID userId = UUID.randomUUID();

        // 3 operations with review threshold = 3
        for (int i = 1; i <= 3; i++) {
            final UUID opId = UUID.randomUUID();
            String[] keys = new String[]{
                    "fraud:op:" + opId,
                    "user:" + userId + ":review_count",
                    "user:" + userId + ":risk_score",
                    "user:" + userId + ":blocked"
            };
            String[] args = new String[]{"30000", "10", "1000", "3"};

            List<Object> result = redisCommands.eval(
                    RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                    ScriptOutputType.MULTI,
                    keys,
                    args
            );

            if (i < 3) {
                assertThat(result.get(3)).isEqualTo(0L);
            } else {
                assertThat(result.get(3)).isEqualTo(1L); // Blocked on 3rd attempt
            }
        }

        assertThat(redisCommands.get("user:" + userId + ":blocked")).isEqualTo("1");
    }

    @Test
    @DisplayName("Should execute BLOCK_PROTECTED_SCRIPT idempotently with replay protection")
    void shouldExecuteBlockScriptIdempotently() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();

        String[] keys = new String[]{
                "fraud:op:" + opId,
                "user:" + userId + ":blocked"
        };
        String[] args = new String[]{"30000", "3600"};

        List<Object> result1 = redisCommands.eval(
                RedisScripts.BLOCK_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );

        assertThat(result1.get(0)).isEqualTo(1L);
        assertThat(result1.get(1)).isEqualTo("BLOCKED");
        assertThat(redisCommands.get("user:" + userId + ":blocked")).isEqualTo("1");

        // Replay attempt
        List<Object> result2 = redisCommands.eval(
                RedisScripts.BLOCK_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keys,
                args
        );

        assertThat(result2.get(0)).isEqualTo(0L);
        assertThat(result2.get(1)).isEqualTo("REPLAY_DETECTED");
        assertThat(redisCommands.get("user:" + userId + ":blocked")).isEqualTo("1");
    }

    @Test
    @DisplayName("Should handle concurrent Lua script execution across multiple threads without deadlock (I-DF-002)")
    void shouldHandleConcurrentScriptExecutionsWithoutDeadlock() throws Exception {
        final int threadCount = 20;
        final int operationsPerThread = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final UUID userId = UUID.randomUUID();

        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                int successCount = 0;
                for (int j = 0; j < operationsPerThread; j++) {
                    UUID opId = UUID.randomUUID();
                    String[] keys = new String[]{
                            "fraud:op:" + opId,
                            "user:" + userId + ":review_count",
                            "user:" + userId + ":risk_score",
                            "user:" + userId + ":blocked"
                    };
                    String[] args = new String[]{"30000", "1", "10000", "5000"};

                    List<Object> res = redisCommands.eval(
                            RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                            ScriptOutputType.MULTI,
                            keys,
                            args
                    );
                    if (res != null && res.getFirst().equals(1L)) {
                        successCount++;
                    }
                }
                return successCount;
            }));
        }

        startLatch.countDown(); // Release all threads concurrently

        int totalSuccess = 0;
        for (Future<Integer> f : futures) {
            totalSuccess += f.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertThat(totalSuccess).isEqualTo(threadCount * operationsPerThread);
        assertThat(redisCommands.get("user:" + userId + ":review_count"))
                .isEqualTo(String.valueOf(threadCount * operationsPerThread));
        assertThat(redisCommands.get("user:" + userId + ":risk_score"))
                .isEqualTo(String.valueOf(threadCount * operationsPerThread));
    }
}
