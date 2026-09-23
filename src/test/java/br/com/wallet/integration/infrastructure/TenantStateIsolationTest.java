package br.com.wallet.integration.infrastructure;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.fraud.domain.VelocityResult;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.fusion.internal.persistence.RedisRiskProfileStore;
import br.com.wallet.fraud.infrastructure.NewRecipientStore;
import br.com.wallet.fraud.infrastructure.RedisVelocityStore;
import br.com.wallet.infrastructure.config.RedisScripts;
import br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Tenant State Isolation Integration Test (Test Triad 2: I-DF20-004, I-DF20-007, REQ-DF20-006, REQ-DF20-016)")
public class TenantStateIsolationTest extends DockerProperties {

    @Autowired
    private RedisCommands<String, String> redisCommands;

    @Autowired
    private RedisVelocityStore velocityStore;

    @Autowired
    private RedisRiskProfileStore riskProfileStore;

    @Autowired
    private NewRecipientStore recipientStore;

    @Autowired
    private HotRiskMaterializer hotRiskMaterializer;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        redisCommands.flushall();
    }

    @Test
    @DisplayName("Triad 2 - Positive: Tenant tenant-alpha state is completely isolated from tenant-beta")
    void shouldIsolateTenantStateCompletely() {
        final UUID userId = UUID.randomUUID();
        final Instant now = Instant.now();

        // 1. Velocity Store Isolation
        velocityStore.recordTransaction("tenant-alpha", userId, now, Duration.ofSeconds(30));
        velocityStore.recordTransaction("tenant-alpha", userId, now.plusMillis(10), Duration.ofSeconds(30));

        // Check velocity for tenant-beta -> should start from scratch (count = 1 for the new check)
        VelocityResult betaResult = velocityStore.checkVelocity(userId, UUID.randomUUID(), now.plusMillis(20), "tenant-beta");
        assertThat(betaResult).isInstanceOf(VelocityResult.Ok.class);
        assertThat(((VelocityResult.Ok) betaResult).count()).isEqualTo(1L);

        // Alpha key has 2 items, beta key has 1 item
        assertThat(redisCommands.zcard("fraud:velocity:tenant-alpha:" + userId)).isEqualTo(2L);
        assertThat(redisCommands.zcard("fraud:velocity:tenant-beta:" + userId)).isEqualTo(1L);

        // 2. Risk Profile Store Isolation
        RiskSubject subjectAlpha = new RiskSubject(RiskSubjectType.USER, userId.toString(), "tenant-alpha");
        RiskSubject subjectBeta = new RiskSubject(RiskSubjectType.USER, userId.toString(), "tenant-beta");

        RiskProfile profileAlpha = new RiskProfile(
                0.2, 0.3, 0.1, 0.4, 0.5, 0.35,
                FraudDecision.ALLOW, "NONE", false, now
        );
        riskProfileStore.putProfile(subjectAlpha, profileAlpha, Duration.ofMinutes(10));

        Optional<RiskProfile> fetchedAlpha = riskProfileStore.getProfile(subjectAlpha);
        Optional<RiskProfile> fetchedBeta = riskProfileStore.getProfile(subjectBeta);

        assertThat(fetchedAlpha).isPresent();
        assertThat(fetchedAlpha.get().finalRisk()).isEqualTo(0.35);
        assertThat(fetchedBeta).isEmpty();

        // 3. Hot Risk Materializer Isolation
        hotRiskMaterializer.materializeGraphRisk(userId, 0.95, "tenant-alpha").toCompletableFuture().join();
        Double alphaGraphRisk = hotRiskMaterializer.getHotGraphRisk(userId, "tenant-alpha").toCompletableFuture().join();
        Double betaGraphRisk = hotRiskMaterializer.getHotGraphRisk(userId, "tenant-beta").toCompletableFuture().join();

        assertThat(alphaGraphRisk).isEqualTo(0.95);
        assertThat(betaGraphRisk).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Triad 2 - Global Key: System health query succeeds without tenant; Replay protection is globally unique (I-DF20-007)")
    void shouldSupportGlobalKeysAndGlobalReplayBarrier() {
        // 1. Global infrastructure key: system:version
        redisCommands.set("system:version", "2.0.0");
        String version = redisCommands.get("system:version");
        assertThat(version).isEqualTo("2.0.0");

        // 2. Replay key fraud:op:{operationId} is a global barrier across tenants (I-DF20-007)
        final UUID sharedOpId = UUID.randomUUID();
        final UUID userAlpha = UUID.randomUUID();
        final UUID userBeta = UUID.randomUUID();

        String replayKey = "fraud:op:" + sharedOpId;
        String[] keysTenantAlpha = new String[]{
                replayKey,
                "user:tenant-alpha:" + userAlpha + ":review_count",
                "user:tenant-alpha:" + userAlpha + ":risk_score",
                "user:tenant-alpha:" + userAlpha + ":blocked"
        };
        String[] args = new String[]{"30000", "20", "100", "5"};

        // Execute for tenant-alpha
        List<Object> resAlpha = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keysTenantAlpha,
                args
        );
        assertThat(resAlpha.get(0)).isEqualTo(1L);

        // Attempt to execute with identical operationId for tenant-beta -> Replay detected
        String[] keysTenantBeta = new String[]{
                replayKey,
                "user:tenant-beta:" + userBeta + ":review_count",
                "user:tenant-beta:" + userBeta + ":risk_score",
                "user:tenant-beta:" + userBeta + ":blocked"
        };
        List<Object> resBeta = redisCommands.eval(
                RedisScripts.REVIEW_COUNT_PROTECTED_SCRIPT,
                ScriptOutputType.MULTI,
                keysTenantBeta,
                args
        );
        assertThat(resBeta.get(0)).isEqualTo(0L);
        assertThat(resBeta.get(1)).isEqualTo("REPLAY_DETECTED");
    }

    @Test
    @DisplayName("Triad 2 - Boundary: Missing or invalid tenant context throws TenantContextMissingException")
    void shouldThrowExceptionWhenTenantContextIsMissingOrInvalid() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Instant now = Instant.now();

        // 1. VelocityStore without tenantId
        assertThatThrownBy(() -> velocityStore.checkVelocity(userId, opId, now))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> velocityStore.checkVelocity(userId, opId, now, null))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> velocityStore.checkVelocity(userId, opId, now, "   "))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> velocityStore.recordTransaction(null, userId, now, Duration.ofSeconds(30)))
                .isInstanceOf(TenantContextMissingException.class);
        // Tenant > 64 chars
        String longTenant = "a".repeat(65);
        assertThatThrownBy(() -> velocityStore.recordTransaction(longTenant, userId, now, Duration.ofSeconds(30)))
                .isInstanceOf(TenantContextMissingException.class);

        // 2. RiskProfileStore with missing/invalid tenant
        RiskSubject invalidSubject = new RiskSubject(RiskSubjectType.USER, userId.toString(), null);
        RiskProfile profile = new RiskProfile(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, FraudDecision.ALLOW, "NONE", false, now);
        // RiskSubject constructor defaults null to "default" if not specified, but let's test invalid empty tenant in subject
        RiskSubject emptySubject = new RiskSubject(RiskSubjectType.USER, userId.toString(), "   ");
        assertThatThrownBy(() -> riskProfileStore.putProfile(emptySubject, profile, Duration.ofMinutes(5)))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> riskProfileStore.getProfile(emptySubject))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> riskProfileStore.evictProfile(emptySubject))
                .isInstanceOf(TenantContextMissingException.class);

        // 3. NewRecipientStore with missing/invalid tenant
        assertThatThrownBy(() -> recipientStore.checkNewRecipient(userId, UUID.randomUUID(), now))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> recipientStore.checkNewRecipient(userId, UUID.randomUUID(), now, null))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> recipientStore.checkNewRecipient(userId, UUID.randomUUID(), now, "   "))
                .isInstanceOf(TenantContextMissingException.class);

        // 4. HotRiskMaterializer with missing/invalid tenant
        assertThatThrownBy(() -> hotRiskMaterializer.materializeGraphRisk(userId, 0.5))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> hotRiskMaterializer.materializeGraphRisk(userId, 0.5, null))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> hotRiskMaterializer.getHotGraphRisk(userId))
                .isInstanceOf(TenantContextMissingException.class);
        assertThatThrownBy(() -> hotRiskMaterializer.getHotGraphRisk(userId, ""))
                .isInstanceOf(TenantContextMissingException.class);
    }
}
