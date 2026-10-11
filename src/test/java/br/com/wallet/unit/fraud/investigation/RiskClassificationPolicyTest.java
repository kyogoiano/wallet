package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.internal.policy.RiskClassificationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskClassificationPolicy Unit Tests (Deterministic Severity Thresholds)")
class RiskClassificationPolicyTest {

    private RiskClassificationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RiskClassificationPolicy();
    }

    @Test
    @DisplayName("Should classify CRITICAL when any score >= 0.90")
    void shouldClassifyCritical() {
        FraudRiskSnapshot snapshot = new FraudRiskSnapshot(0.1, 0.2, 0.95, 0.4, 2.0, "MONEY_MULE_RAPID_DRAIN", 0.9);
        assertThat(policy.classify(snapshot)).isEqualTo(RiskClassification.CRITICAL);
    }

    @Test
    @DisplayName("Should classify HIGH when max score between 0.75 and 0.89")
    void shouldClassifyHigh() {
        FraudRiskSnapshot snapshot = new FraudRiskSnapshot(0.2, 0.78, 0.4, 0.6, 1.5, "SMURFING", 0.7);
        assertThat(policy.classify(snapshot)).isEqualTo(RiskClassification.HIGH);
    }

    @Test
    @DisplayName("Should classify MEDIUM when max score between 0.50 and 0.74")
    void shouldClassifyMedium() {
        FraudRiskSnapshot snapshot = new FraudRiskSnapshot(0.55, 0.3, 0.4, 0.2, 1.0, "ACCOUNT_TAKEOVER", 0.5);
        assertThat(policy.classify(snapshot)).isEqualTo(RiskClassification.MEDIUM);
    }

    @Test
    @DisplayName("Should classify LOW when all scores < 0.50")
    void shouldClassifyLow() {
        FraudRiskSnapshot snapshot = new FraudRiskSnapshot(0.1, 0.2, 0.15, 0.0, 0.0, "NONE", 0.0);
        assertThat(policy.classify(snapshot)).isEqualTo(RiskClassification.LOW);
    }
}
