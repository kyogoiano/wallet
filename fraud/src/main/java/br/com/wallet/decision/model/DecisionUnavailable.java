package br.com.wallet.decision.model;

import java.util.Objects;

/**
 * Explicit outcome when a decision cannot be fulfilled due to timeout, failure, or lack of evidence.
 *
 * <p><b>STRUCTURAL INVARIANT (I-TYPED-005, REQ-TYPED-013):</b>
 * Structurally exposes NO {@code score()}, {@code value()}, or {@code confidence()} accessors,
 * preventing downstream consumers from accidentally treating unavailable states as scored outcomes.
 *
 * @param <T> concrete decision value type extending {@link DecisionValue}
 * @param reason diagnostic failure category
 * @param diagnosticMessage human-readable error or timeout explanation
 * @param provenance execution provenance and latency metadata
 */
public record DecisionUnavailable<T extends DecisionValue>(
    UnavailableReason reason,
    String diagnosticMessage,
    DecisionProvenance provenance
) implements DecisionOutcome<T> {

    public DecisionUnavailable {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(diagnosticMessage, "diagnosticMessage must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
