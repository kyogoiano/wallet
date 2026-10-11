package br.com.wallet.fraud.investigation.internal.policy;

import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Deterministic policy deriving RiskClassification from multi-dimensional risk scores (EVIDENCE_POLICY).
 */
@Component
public class RiskClassificationPolicy {

    public static final double CRITICAL_THRESHOLD = 0.90;
    public static final double HIGH_THRESHOLD = 0.75;
    public static final double MEDIUM_THRESHOLD = 0.50;

    @NonNull
    public RiskClassification classify(@NonNull final FraudRiskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");

        double maxScore = Math.max(
            Math.max(snapshot.directRisk(), snapshot.graphRisk()),
            Math.max(snapshot.propagatedRisk(), snapshot.behavioralRisk())
        );

        if (maxScore >= CRITICAL_THRESHOLD) {
            return RiskClassification.CRITICAL;
        } else if (maxScore >= HIGH_THRESHOLD) {
            return RiskClassification.HIGH;
        } else if (maxScore >= MEDIUM_THRESHOLD) {
            return RiskClassification.MEDIUM;
        } else {
            return RiskClassification.LOW;
        }
    }
}
