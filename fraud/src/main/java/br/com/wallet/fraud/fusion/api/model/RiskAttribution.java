package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates the explainable risk attribution, primary risk driver, and any degradation reason.
 */
public record RiskAttribution(
    @NonNull String primaryDriver,
    @NonNull List<FactorContribution> contributions,
    @NonNull Optional<String> degradedReason
) {
    public RiskAttribution {
        Objects.requireNonNull(primaryDriver, "primaryDriver cannot be null");
        Objects.requireNonNull(contributions, "contributions cannot be null");
        Objects.requireNonNull(degradedReason, "degradedReason cannot be null");
        contributions = List.copyOf(contributions);
    }

    public RiskAttribution(@NonNull String primaryDriver, @NonNull List<FactorContribution> contributions) {
        this(primaryDriver, contributions, Optional.empty());
    }

    public RiskAttribution(
        @NonNull String primaryDriver,
        @NonNull List<FactorContribution> contributions,
        @Nullable String degradedReason
    ) {
        this(primaryDriver, contributions, Optional.ofNullable(degradedReason));
    }
}
