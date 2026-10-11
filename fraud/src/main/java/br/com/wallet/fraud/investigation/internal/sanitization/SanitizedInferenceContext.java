package br.com.wallet.fraud.investigation.internal.sanitization;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Air-gapped, sanitized context prepared for local SLM inference.
 * Guarantees that raw UUIDs, CPFs, Pix keys, and names are replaced with surrogate tokens.
 */
public record SanitizedInferenceContext(
    @NonNull String maskedTargetId,
    @NonNull FraudRiskSnapshot risks,
    @NonNull List<AtomicEvidenceItem> sanitizedEvidenceItems,
    @NonNull RiskClassification classification,
    @NonNull List<RecommendedAction> allowedActions
) {
    public SanitizedInferenceContext {
        Objects.requireNonNull(maskedTargetId, "maskedTargetId cannot be null");
        Objects.requireNonNull(risks, "risks cannot be null");
        Objects.requireNonNull(sanitizedEvidenceItems, "sanitizedEvidenceItems cannot be null");
        Objects.requireNonNull(classification, "classification cannot be null");
        Objects.requireNonNull(allowedActions, "allowedActions cannot be null");
        sanitizedEvidenceItems = List.copyOf(sanitizedEvidenceItems);
        allowedActions = List.copyOf(allowedActions);
    }
}
