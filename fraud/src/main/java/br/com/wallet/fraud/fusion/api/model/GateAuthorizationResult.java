package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Result returned by the gateway authorization check (REQ-FUSION-007).
 */
public record GateAuthorizationResult(
    boolean authorized,
    @NonNull FraudDecision decision,
    @NonNull String reason
) {
    public GateAuthorizationResult {
        Objects.requireNonNull(decision, "decision cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }

    public static GateAuthorizationResult allow(@NonNull String reason) {
        return new GateAuthorizationResult(true, FraudDecision.ALLOW, reason);
    }

    public static GateAuthorizationResult block(@NonNull FraudDecision decision, @NonNull String reason) {
        return new GateAuthorizationResult(false, decision, reason);
    }
}
