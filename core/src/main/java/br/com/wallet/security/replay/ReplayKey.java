package br.com.wallet.security.replay;

import br.com.wallet.security.envelope.TenantId;

import java.util.Objects;

/**
 * Composite key uniquely identifying a nonce within a tenant and principal boundary (REQ-SEC-028, I-ENV-004).
 */
public record ReplayKey(
        TenantId tenantId,
        PrincipalId principalId,
        String nonce
) {
    public ReplayKey {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(principalId, "principalId must not be null");
        Objects.requireNonNull(nonce, "nonce must not be null");
        if (nonce.isBlank()) {
            throw new IllegalArgumentException("nonce must not be blank");
        }
    }

    /**
     * Produces the canonical storage key format: {@code nonce:{tenant}:{principal}:{nonce}}.
     */
    public String toStorageKey() {
        return "nonce:" + tenantId.value() + ":" + principalId.value() + ":" + nonce;
    }
}
