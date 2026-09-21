package br.com.wallet.fraud.rules;

import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.VelocityResult;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.infrastructure.RedisUserStore;
import br.com.wallet.fraud.infrastructure.RedisVelocityStore;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantScopedFraudStateTest (I-SEC-010, REQ-SEC-018, TASK-SEC-5.4)")
class TenantScopedFraudStateTest {

    @Mock
    private RedisAsyncCommands<String, String> redisCommands;

    @Mock
    private RedisVelocityStore redisVelocityStore;

    private static <T> RedisFuture<T> completedRedisFuture(T value) {
        class CompletedRedisFuture extends CompletableFuture<T> implements RedisFuture<T> {
            CompletedRedisFuture(T val) {
                complete(val);
            }

            @Override
            public String getError() {
                return null;
            }

            @Override
            public boolean await(long timeout, TimeUnit unit) {
                return true;
            }
        }
        return new CompletedRedisFuture(value);
    }

    @Test
    @DisplayName("User ID collision across tenants MUST NOT share block decisions (REQ-SEC-018)")
    void shouldNotShareBlockDecisionsAcrossTenantsOnIdCollision() {
        RedisUserStore userStore = new RedisUserStore(redisCommands);
        UUID collidingUserId = UUID.randomUUID();

        // Simulate tenant-A has key "fraud:tenant-A:user:<id>:blocked"
        when(redisCommands.exists("fraud:tenant-A:user:" + collidingUserId + ":blocked"))
                .thenReturn(completedRedisFuture(1L));

        // Simulate tenant-B does NOT have key "fraud:tenant-B:user:<id>:blocked"
        when(redisCommands.exists("fraud:tenant-B:user:" + collidingUserId + ":blocked"))
                .thenReturn(completedRedisFuture(0L));

        boolean blockedInTenantA = userStore.isBlocked(collidingUserId, "tenant-A");
        boolean blockedInTenantB = userStore.isBlocked(collidingUserId, "tenant-B");

        assertThat(blockedInTenantA).isTrue();
        assertThat(blockedInTenantB).isFalse();

        // Verify UserBlockRule evaluates according to tenant context
        UserBlockRule rule = new UserBlockRule(userStore);

        FraudContext ctxTenantA = new FraudContext(
                collidingUserId, null, UUID.randomUUID(), 10000, Instant.now(), "tenant-A"
        );
        FraudContext ctxTenantB = new FraudContext(
                collidingUserId, null, UUID.randomUUID(), 10000, Instant.now(), "tenant-B"
        );

        RuleResult resultA = rule.evaluate(ctxTenantA);
        RuleResult resultB = rule.evaluate(ctxTenantB);

        assertThat(resultA.triggered()).isTrue();
        assertThat(resultA.scoreImpact()).isEqualTo(30);

        assertThat(resultB.triggered()).isFalse();
        assertThat(resultB.scoreImpact()).isZero();
    }

    @Test
    @DisplayName("User ID collision across tenants MUST NOT share velocity counters (I-SEC-010)")
    void shouldNotShareVelocityCountersAcrossTenantsOnIdCollision() {
        GlobalVelocityRule rule = new GlobalVelocityRule(redisVelocityStore);
        UUID collidingUserId = UUID.randomUUID();
        Instant now = Instant.now();

        UUID opIdTenantA = UUID.randomUUID();
        UUID opIdTenantB = UUID.randomUUID();

        FraudContext ctxTenantA = new FraudContext(
                collidingUserId, null, opIdTenantA, 5000, now, "tenant-A"
        );
        FraudContext ctxTenantB = new FraudContext(
                collidingUserId, null, opIdTenantB, 5000, now, "tenant-B"
        );

        // Tenant A exceeded velocity threshold
        when(redisVelocityStore.checkVelocity(eq(collidingUserId), eq(opIdTenantA), eq(now), eq("tenant-A")))
                .thenReturn(new VelocityResult.Exceeded(15));

        // Tenant B is within normal velocity threshold
        when(redisVelocityStore.checkVelocity(eq(collidingUserId), eq(opIdTenantB), eq(now), eq("tenant-B")))
                .thenReturn(new VelocityResult.Ok(2));

        RuleResult evalA = rule.evaluate(ctxTenantA);
        RuleResult evalB = rule.evaluate(ctxTenantB);

        assertThat(evalA.triggered()).isTrue();
        assertThat(evalA.scoreImpact()).isEqualTo(30);

        assertThat(evalB.triggered()).isFalse();
        assertThat(evalB.scoreImpact()).isZero();

        verify(redisVelocityStore).checkVelocity(collidingUserId, opIdTenantA, now, "tenant-A");
        verify(redisVelocityStore).checkVelocity(collidingUserId, opIdTenantB, now, "tenant-B");
    }

    @Test
    @DisplayName("RiskSubject toKey() must be strictly namespaced by tenantId (risk_profile:{tenantId}:...)")
    void shouldNamespaceRiskSubjectKeyByTenantId() {
        UUID userId = UUID.randomUUID();

        RiskSubject subjectTenant1 = new RiskSubject(RiskSubjectType.USER, userId.toString(), "tenant-corp-1");
        RiskSubject subjectTenant2 = new RiskSubject(RiskSubjectType.USER, userId.toString(), "tenant-corp-2");

        assertThat(subjectTenant1.toKey()).isEqualTo("risk_profile:tenant-corp-1:USER:" + userId);
        assertThat(subjectTenant2.toKey()).isEqualTo("risk_profile:tenant-corp-2:USER:" + userId);
        assertThat(subjectTenant1.toKey()).isNotEqualTo(subjectTenant2.toKey());
    }

    @Test
    @DisplayName("HotRiskMaterializer keys must be strictly namespaced by tenantId")
    void shouldNamespaceHotRiskMaterializerKeysByTenantId() {
        br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer materializer =
                new br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer(redisCommands);

        UUID entityId = UUID.randomUUID();

        when(redisCommands.set(eq("risk:tenant-A:user:" + entityId + ":graph_risk"), any(), any()))
                .thenReturn(completedRedisFuture("OK"));

        materializer.materializeGraphRisk(entityId, 0.85, "tenant-A");

        verify(redisCommands).set(eq("risk:tenant-A:user:" + entityId + ":graph_risk"), eq("0.85"), any());
        verify(redisCommands, never()).set(eq("risk:tenant-B:user:" + entityId + ":graph_risk"), any(), any());
    }
}
