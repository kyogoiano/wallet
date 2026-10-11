package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.internal.policy.RiskDecisionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskDecisionPolicy Unit Tests (REQ-FUSION-005, REQ-FUSION-011, I-FUSION-001)")
class RiskDecisionPolicyTest {

    private RiskDecisionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RiskDecisionPolicy();
    }

    @Test
    @DisplayName("I-FUSION-001: Direct rule primacy — directRisk >= 1.0 maps strictly to HARD_BLOCK")
    void shouldPrioritizeHardBlockOnDirectViolation() {
        FraudDecision decision = policy.evaluate(1.0, 0.20);
        assertThat(decision).isEqualTo(FraudDecision.HARD_BLOCK);
        assertThat(policy.shouldTriggerInvestigation(decision)).isFalse();
    }

    @Test
    @DisplayName("REQ-FUSION-011: Final risk >= 0.85 maps to RESTRICT with required investigation")
    void shouldMapToRestrictForHighRisk() {
        FraudDecision decision = policy.evaluate(0.20, 0.88);
        assertThat(decision).isEqualTo(FraudDecision.RESTRICT);
        assertThat(policy.shouldTriggerInvestigation(decision)).isTrue();
    }

    @Test
    @DisplayName("REQ-FUSION-011: Final risk in [0.50, 0.85) maps to REVIEW with required investigation")
    void shouldMapToReviewForModerateRisk() {
        FraudDecision decision = policy.evaluate(0.10, 0.65);
        assertThat(decision).isEqualTo(FraudDecision.REVIEW);
        assertThat(policy.shouldTriggerInvestigation(decision)).isTrue();
    }

    @Test
    @DisplayName("REQ-FUSION-011: Final risk < 0.50 maps to ALLOW without investigation")
    void shouldMapToAllowForLowRisk() {
        FraudDecision decision = policy.evaluate(0.05, 0.35);
        assertThat(decision).isEqualTo(FraudDecision.ALLOW);
        assertThat(policy.shouldTriggerInvestigation(decision)).isFalse();
    }
}
