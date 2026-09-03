package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic facts and evidence items assembled for an investigation.
 */
public record InvestigationEvidence(
    @NonNull FraudRiskSnapshot risks,
    @NonNull List<AtomicEvidenceItem> evidenceItems
) {
    public InvestigationEvidence {
        Objects.requireNonNull(risks, "risks cannot be null");
        Objects.requireNonNull(evidenceItems, "evidenceItems cannot be null");
        evidenceItems = List.copyOf(evidenceItems);
    }
}
