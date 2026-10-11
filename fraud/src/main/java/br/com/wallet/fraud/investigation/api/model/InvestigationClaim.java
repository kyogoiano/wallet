package br.com.wallet.fraud.investigation.api.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Structured investigation claim produced by local inference or fallback,
 * requiring explicit claim type and reference IDs pointing to deterministic facts.
 */
public record InvestigationClaim(
    @NonNull ClaimType type,
    @NonNull String summary,
    @NonNull List<String> evidenceReferences
) {
    public InvestigationClaim {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(summary, "summary cannot be null");
        Objects.requireNonNull(evidenceReferences, "evidenceReferences cannot be null");
        evidenceReferences = List.copyOf(evidenceReferences);
    }
}
