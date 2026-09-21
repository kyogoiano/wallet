package br.com.wallet.fraud.fusion.api.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Strongly typed entity identifier for risk profiling (REQ-FUSION-006).
 */
public record RiskSubject(
    @NonNull RiskSubjectType type,
    @NonNull String id,
    @Nullable String tenantId
) {
    public RiskSubject {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        if (tenantId == null) {
            tenantId = "default";
        }
    }

    public RiskSubject(@NonNull RiskSubjectType type, @NonNull String id) {
        this(type, id, "default");
    }

    @NonNull
    public String toKey() {
        return "risk_profile:" + (tenantId != null ? tenantId : "default") + ":" + type.name() + ":" + id;
    }
}
