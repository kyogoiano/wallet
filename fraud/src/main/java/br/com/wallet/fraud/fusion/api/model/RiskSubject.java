package br.com.wallet.fraud.fusion.api.model;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Strongly typed entity identifier for risk profiling (REQ-FUSION-006).
 */
public record RiskSubject(
    @NonNull RiskSubjectType type,
    @NonNull String id,
    @NonNull String tenantId
) {
    public RiskSubject {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
        if (tenantId.isBlank()) {
            throw new TenantContextMissingException("Tenant identifier is required for RiskSubject");
        }
    }

    @NonNull
    public String toKey() {
        return "risk_profile:" + tenantId + ":" + type.name() + ":" + id;
    }
}
