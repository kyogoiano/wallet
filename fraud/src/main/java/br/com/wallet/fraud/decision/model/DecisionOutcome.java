package br.com.wallet.fraud.decision.model;

/**
 * Sealed algebraic outcome hierarchy representing the result of evaluating a typed question.
 * Permits only successful answers ({@link DecisionAnswer}) or explicit unavailable states ({@link DecisionUnavailable}).
 *
 * @param <T> concrete decision value type extending {@link DecisionValue}
 */
public sealed interface DecisionOutcome<T extends DecisionValue>
    permits DecisionAnswer, DecisionUnavailable {
}
