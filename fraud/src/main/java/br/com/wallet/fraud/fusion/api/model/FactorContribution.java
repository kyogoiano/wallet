package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Quantifies the Leave-One-Out (LOO) marginal risk attribution and normalized percentage for a signal factor.
 */
public record FactorContribution(
    @NonNull String factorName,
    double rawScore,
    double marginalImpact,
    double normalizedPercentage
) {
    public FactorContribution {
        Objects.requireNonNull(factorName, "factorName cannot be null");
    }
}
