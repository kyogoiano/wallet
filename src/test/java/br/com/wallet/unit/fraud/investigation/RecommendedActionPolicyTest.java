package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.internal.policy.RecommendedActionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendedActionPolicy Unit Tests (Deterministic Action Selection)")
class RecommendedActionPolicyTest {

    private RecommendedActionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RecommendedActionPolicy();
    }

    @Test
    @DisplayName("REQ-VEC-010: CRITICAL severity should permit restriction and escalation")
    void shouldPermitCriticalActions() {
        FraudRiskSnapshot snapshot = new FraudRiskSnapshot(0.1, 0.2, 0.95, 0.4, 2.0, "MONEY_MULE_RAPID_DRAIN", 0.9);
        List<RecommendedAction> actions = policy.determineAllowedActions(RiskClassification.CRITICAL, snapshot);

        assertThat(actions).contains(
            RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION,
            RecommendedAction.ESCALATE_TO_COMPLIANCE,
            RecommendedAction.MANUAL_REVIEW
        );
    }

    @Test
    @DisplayName("REQ-VEC-010: LOW severity should only permit increased monitoring")
    void shouldPermitLowActions() {
        FraudRiskSnapshot snapshot = FraudRiskSnapshot.empty();
        List<RecommendedAction> actions = policy.determineAllowedActions(RiskClassification.LOW, snapshot);

        assertThat(actions).containsExactly(RecommendedAction.INCREASE_MONITORING);
    }
}
