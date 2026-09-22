package br.com.wallet.fraud.decision.model;

import java.util.Objects;

/**
 * Strongly typed structural binding between a {@link DecisionQuestion} and its resulting {@link DecisionOutcome}.
 *
 * @param <T> concrete decision value type extending {@link DecisionValue}
 * @param question question definition and type witness
 * @param outcome resulting answer or unavailable state
 */
public record EvaluatedQuestion<T extends DecisionValue>(
    DecisionQuestion<T> question,
    DecisionOutcome<T> outcome
) {

    public EvaluatedQuestion {
        Objects.requireNonNull(question, "question must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}
