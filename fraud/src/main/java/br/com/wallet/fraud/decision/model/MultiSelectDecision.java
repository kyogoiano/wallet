package br.com.wallet.fraud.decision.model;

import java.util.Objects;
import java.util.Set;

/**
 * Multi-select categorical or risk factor decision value.
 *
 * @param selected immutable set of selected categorical labels
 * @param rationale selection justification
 */
public record MultiSelectDecision(
    Set<String> selected,
    String rationale
) implements DecisionValue {

    public MultiSelectDecision {
        Objects.requireNonNull(selected, "selected must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");
        selected = Set.copyOf(selected);
    }
}
