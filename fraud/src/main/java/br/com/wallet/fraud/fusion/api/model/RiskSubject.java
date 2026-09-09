package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Strongly typed entity identifier for risk profiling (REQ-FUSION-006).
 */
public record RiskSubject(
    @NonNull RiskSubjectType type,
    @NonNull String id
) {
    public RiskSubject {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
    }

    @NonNull
    public String toKey() {
        return "risk_profile:" + type.name() + ":" + id;
    }
}
