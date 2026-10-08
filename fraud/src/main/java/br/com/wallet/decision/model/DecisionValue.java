package br.com.wallet.decision.model;

/**
 * Marker sealed interface for strongly typed AI decision outcome values.
 * Permits only deterministic, validated domain decision types.
 */
public sealed interface DecisionValue
    permits BooleanDecision, ScoreDecision, CategoryDecision, TextDecision, MultiSelectDecision, SubscriptionClassification {
}
