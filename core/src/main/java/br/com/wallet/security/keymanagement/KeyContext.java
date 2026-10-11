package br.com.wallet.security.keymanagement;

import br.com.wallet.security.envelope.TenantId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly typed KMS Encryption Context value object enforcing cryptographically bound
 * tenant identification and domain separation (REQ-SEC-024, I-ENV-002, I-SEC-011).
 */
public record KeyContext(
        TenantId tenantId,
        Map<String, String> contextMap
) {
    public static final String KEY_DOMAIN_KEY = "key_domain";
    public static final String KEY_DOMAIN_VALUE = "WALLET-ENV-V1";
    public static final String TENANT_ID_KEY = "tenant_id";

    public KeyContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(contextMap, "contextMap must not be null");
        contextMap = Collections.unmodifiableMap(new LinkedHashMap<>(contextMap));
    }

    /**
     * Creates a standard KMS Encryption Context bound strictly to the given tenant.
     */
    public static KeyContext forTenant(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TENANT_ID_KEY, tenantId.value());
        map.put(KEY_DOMAIN_KEY, KEY_DOMAIN_VALUE);
        return new KeyContext(tenantId, map);
    }
}
