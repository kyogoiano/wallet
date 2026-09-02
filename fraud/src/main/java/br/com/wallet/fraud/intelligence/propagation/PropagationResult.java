package br.com.wallet.fraud.intelligence.propagation;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of a complete risk propagation execution rooted at a specific source entity or batch.
 */
public record PropagationResult(
    @NonNull UUID rootSourceId,
    @NonNull Instant evaluatedAt,
    @NonNull Map<UUID, PropagatedEntityRisk> propagatedRisks,
    int pathsEvaluated,
    @NonNull String modelVersion
) {
    public PropagationResult {
        Objects.requireNonNull(rootSourceId, "rootSourceId cannot be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt cannot be null");
        Objects.requireNonNull(propagatedRisks, "propagatedRisks cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        propagatedRisks = Collections.unmodifiableMap(propagatedRisks);
    }
}
