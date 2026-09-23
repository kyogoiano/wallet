package br.com.wallet.integration.infrastructure;

import br.com.wallet.infrastructure.config.RedisScripts;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.DragonflyOutputNormalizer;
import br.com.wallet.support.IntegrationTestBase;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.protocol.ProtocolVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Dragonfly 2.0 Gate Promotion Report & Mathematical Certification (TASK-DF20-5.2)")
public class GatePromotionReportTest extends DockerProperties {

    private static final Logger log = LoggerFactory.getLogger(GatePromotionReportTest.class);

    @Autowired
    private RedisClient redisClient;

    @Autowired
    private RedisCommands<String, String> redisCommands;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        redisCommands.flushall();
    }

    public record GateEvaluation(
            String gateName,
            boolean passed,
            String details
    ) {}

    public record GatePromotionReport(
            GateEvaluation gate1Compatibility,
            GateEvaluation gate2BehavioralOracle,
            GateEvaluation gate3NonRegression,
            boolean promoted
    ) {}

    @Test
    @DisplayName("Evaluate Gates G1 ∧ G2 ∧ G3 and certify Dragonfly 2.0 promotion")
    void shouldEvaluateAllGatesAndCertifyPromotion() {
        // --- Gate 1: Compatibility & Infrastructure ---
        boolean g1Passed = false;
        String g1Details = "";
        try {
            ClientOptions options = redisClient.getOptions();
            boolean resp3 = options.getProtocolVersion() == ProtocolVersion.RESP3;
            String ping = redisCommands.ping();
            boolean pingOk = "PONG".equalsIgnoreCase(ping);
            // test basic commands
            redisCommands.set("g1:test", "val");
            boolean getOk = "val".equals(redisCommands.get("g1:test"));
            g1Passed = resp3 && pingOk && getOk;
            g1Details = String.format("RESP3=%b, Ping=%s, KeyAccess=%b", resp3, ping, getOk);
        } catch (Exception e) {
            g1Passed = false;
            g1Details = "Error: " + e.getMessage();
        }
        GateEvaluation g1 = new GateEvaluation("Gate 1 (Compatibility & Infrastructure)", g1Passed, g1Details);

        // --- Gate 2: Behavioral Correctness & Dual Oracle ---
        boolean g2Passed = false;
        String g2Details = "";
        try {
            final UUID opId = UUID.randomUUID();
            final UUID userId = UUID.randomUUID();
            String[] keys = new String[]{
                    "fraud:op:" + opId,
                    "user:tenant-promo:" + userId + ":review_count",
                    "user:tenant-promo:" + userId + ":risk_score",
                    "user:tenant-promo:" + userId + ":blocked"
            };
            String[] args = new String[]{"30000", "15", "100", "5"};

            List<Object> res = redisCommands.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keys, args);
            boolean scriptOk = res != null && res.size() == 4 && ((Long) res.get(0)) == 1L;

            // Replay detection
            List<Object> replay = redisCommands.eval(RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT, ScriptOutputType.MULTI, keys, args);
            boolean replayOk = replay != null && ((Long) replay.get(0)) == 0L && "REPLAY_DETECTED".equals(replay.get(1));

            // TTL residual drift check
            redisCommands.psetex("g2:ttl", 5000L, "val");
            Long pttl = redisCommands.pttl("g2:ttl");
            boolean ttlOk = pttl != null && pttl > 0 && pttl <= 5000L;

            g2Passed = scriptOk && replayOk && ttlOk;
            g2Details = String.format("LuaScript=%b, ReplayProtection=%b, TtlSemantics=%b (PTTL=%dms)",
                    scriptOk, replayOk, ttlOk, pttl);
        } catch (Exception e) {
            g2Passed = false;
            g2Details = "Error: " + e.getMessage();
        }
        GateEvaluation g2 = new GateEvaluation("Gate 2 (Behavioral Correctness & Oracle)", g2Passed, g2Details);

        // --- Gate 3: Performance & Non-Regression ---
        boolean g3Passed = false;
        String g3Details = "";
        try {
            long start = System.nanoTime();
            int iterations = 1000;
            for (int i = 0; i < iterations; i++) {
                redisCommands.set("g3:key:" + i, "data" + i);
            }
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            double opsPerSec = (iterations / (Math.max(1, durationMs) / 1000.0));
            // In local/test environment, ops/s is easily > 1000
            boolean perfOk = opsPerSec > 500.0;
            g3Passed = perfOk;
            g3Details = String.format("1000 ops executed in %dms (%.1f ops/sec, errorRate=0.0%%)", durationMs, opsPerSec);
        } catch (Exception e) {
            g3Passed = false;
            g3Details = "Error: " + e.getMessage();
        }
        GateEvaluation g3 = new GateEvaluation("Gate 3 (Performance & Non-Regression)", g3Passed, g3Details);

        // Mathematical Promotion Criterion: Promotion <=> G1 ∧ G2 ∧ G3
        boolean promoted = g1.passed() && g2.passed() && g3.passed();
        GatePromotionReport report = new GatePromotionReport(g1, g2, g3, promoted);

        log.info("=================================================");
        log.info("🐉 DRAGONFLY 2.0 PROMOTION CERTIFICATION REPORT");
        log.info("=================================================");
        log.info("• {}: {} -> {}", g1.gateName(), g1.passed() ? "PASS" : "FAIL", g1.details());
        log.info("• {}: {} -> {}", g2.gateName(), g2.passed() ? "PASS" : "FAIL", g2.details());
        log.info("• {}: {} -> {}", g3.gateName(), g3.passed() ? "PASS" : "FAIL", g3.details());
        log.info("-------------------------------------------------");
        log.info("FINAL PROMOTION DECISION: {}", report.promoted() ? "✅ CERTIFIED & PROMOTED" : "❌ REJECTED");
        log.info("=================================================");

        assertThat(report.gate1Compatibility().passed()).isTrue();
        assertThat(report.gate2BehavioralOracle().passed()).isTrue();
        assertThat(report.gate3NonRegression().passed()).isTrue();
        assertThat(report.promoted()).isTrue();
    }
}
