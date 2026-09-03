package br.com.wallet.fraud.investigation.internal.policy;

import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic policy deriving the allowed RecommendedAction set based on RiskClassification
 * and individual risk attributes (REQ-VEC-010).
 */
@Component
public class RecommendedActionPolicy {

    @NonNull
    public List<RecommendedAction> determineAllowedActions(
        @NonNull final RiskClassification classification,
        @NonNull final FraudRiskSnapshot snapshot
    ) {
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(snapshot, "snapshot cannot be null");

        List<RecommendedAction> actions = new ArrayList<>();

        switch (classification) {
            case CRITICAL -> {
                actions.add(RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION);
                actions.add(RecommendedAction.ESCALATE_TO_COMPLIANCE);
                actions.add(RecommendedAction.MANUAL_REVIEW);
            }
            case HIGH -> {
                actions.add(RecommendedAction.MANUAL_REVIEW);
                actions.add(RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION);
                actions.add(RecommendedAction.REQUEST_ADDITIONAL_VERIFICATION);
            }
            case MEDIUM -> {
                actions.add(RecommendedAction.INCREASE_MONITORING);
                actions.add(RecommendedAction.REQUEST_ADDITIONAL_VERIFICATION);
                actions.add(RecommendedAction.MANUAL_REVIEW);
            }
            case LOW -> {
                actions.add(RecommendedAction.INCREASE_MONITORING);
            }
        }

        return Collections.unmodifiableList(actions);
    }
}
