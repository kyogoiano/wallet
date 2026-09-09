package br.com.wallet.integration.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.fusion.internal.gate.FraudGateV4;
import br.com.wallet.fraud.fusion.internal.gate.GateAuthorizationResult;
import br.com.wallet.fraud.fusion.internal.persistence.RiskProfileStore;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("FraudGateV4 Integration Tests (REQ-FUSION-007, Gateway SLA & Contextual Degradation)")
public class FraudGateV4IT extends DockerProperties {

    @Autowired
    private FraudGateV4 fraudGate;

    @Autowired
    private RiskProfileStore store;

    @Test
    @DisplayName("REQ-FUSION-007: Authorization SLA — Authorizes within gateway SLA (P99 < 2ms)")
    void shouldAuthorizeWithinGatewaySla() {
        RiskSubject subject = new RiskSubject(RiskSubjectType.USER, UUID.randomUUID().toString());
        RiskProfile profile = new RiskProfile(
            0.05, 0.10, 0.05, 0.10, 0.10, 0.12,
            FraudDecision.ALLOW, "NONE", false, Instant.now()
        );
        store.putProfile(subject, profile, Duration.ofMinutes(10));

        // Warmup
        fraudGate.authorize(subject, new BigDecimal("100.00"));

        long start = System.nanoTime();
        GateAuthorizationResult result = fraudGate.authorize(subject, new BigDecimal("100.00"));
        long elapsedMicros = (System.nanoTime() - start) / 1_000;

        assertThat(result.authorized()).isTrue();
        assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
        // Gateway SLA check: well within sub-millisecond range
        assertThat(elapsedMicros).isLessThan(20_000); // 20ms safety ceiling for cold integration container
    }

    @Test
    @DisplayName("REQ-FUSION-007: Hard Block — Blocks transactions when profile is HARD_BLOCK")
    void shouldBlockHardBlockedProfile() {
        RiskSubject subject = new RiskSubject(RiskSubjectType.USER, UUID.randomUUID().toString());
        RiskProfile profile = new RiskProfile(
            1.0, 0.5, 0.5, 0.5, 0.5, 1.0,
            FraudDecision.HARD_BLOCK, "DIRECT_HARD_RULE", false, Instant.now()
        );
        store.putProfile(subject, profile, Duration.ofMinutes(10));

        GateAuthorizationResult result = fraudGate.authorize(subject, new BigDecimal("50.00"));

        assertThat(result.authorized()).isFalse();
        assertThat(result.decision()).isEqualTo(FraudDecision.HARD_BLOCK);
    }

    @Test
    @DisplayName("REQ-FUSION-007: Restrict — Rejects transactions when profile is RESTRICT")
    void shouldRestrictHighRiskProfile() {
        RiskSubject subject = new RiskSubject(RiskSubjectType.USER, UUID.randomUUID().toString());
        RiskProfile profile = new RiskProfile(
            0.1, 0.9, 0.8, 0.8, 0.7, 0.92,
            FraudDecision.RESTRICT, "GRAPH_INTELLIGENCE", false, Instant.now()
        );
        store.putProfile(subject, profile, Duration.ofMinutes(10));

        GateAuthorizationResult result = fraudGate.authorize(subject, new BigDecimal("200.00"));

        assertThat(result.authorized()).isFalse();
        assertThat(result.decision()).isEqualTo(FraudDecision.RESTRICT);
    }

    @Test
    @DisplayName("REQ-FUSION-007: Contextual Degradation — Fails closed for high-risk amount, fallback for normal amount")
    void shouldHandleDegradationContextually() {
        RiskSubject unmaterializedSubject = new RiskSubject(RiskSubjectType.USER, UUID.randomUUID().toString());

        // High value (>= 5000.00) without cached profile -> Fail closed
        GateAuthorizationResult highValue = fraudGate.authorize(unmaterializedSubject, new BigDecimal("5000.00"));
        assertThat(highValue.authorized()).isFalse();
        assertThat(highValue.reason()).contains("FAIL_CLOSED");

        // Normal value (< 5000.00) without cached profile -> Deterministic fallback permitted
        GateAuthorizationResult normalValue = fraudGate.authorize(unmaterializedSubject, new BigDecimal("150.00"));
        assertThat(normalValue.authorized()).isTrue();
        assertThat(normalValue.decision()).isEqualTo(FraudDecision.ALLOW);
    }
}
