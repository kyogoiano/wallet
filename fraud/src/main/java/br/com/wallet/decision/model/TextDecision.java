package br.com.wallet.decision.model;

import java.util.Objects;

/**
 * Free-form textual narrative or summary decision value.
 *
 * @param summary human-readable synthesized narrative
 */
public record TextDecision(
    String summary
) implements DecisionValue {

    public TextDecision {
        Objects.requireNonNull(summary, "summary must not be null");
    }
}
