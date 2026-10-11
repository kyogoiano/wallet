package br.com.wallet.edge.internal.security.keymanagement;

import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.keymanagement.GeneratedDataKey;
import br.com.wallet.security.keymanagement.KeyContext;
import br.com.wallet.security.keymanagement.SensitiveKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.9: LocalApplianceKeyManagementClient & PEM Serialization Test (REQ-SEC-025, JEP 538)")
class LocalApplianceKmsPemTest {

    private LocalApplianceKeyManagementClient applianceKms;
    private final TenantId tenantAlpha = new TenantId("tenant-alpha");
    private final TenantId tenantBeta = new TenantId("tenant-beta");
    private final KeyId keyId = new KeyId("appliance-kek-1");

    @BeforeEach
    void setUp() {
        this.applianceKms = new LocalApplianceKeyManagementClient();
    }

    @Test
    @DisplayName("Assert appliance KMS generates and unwraps DEK successfully under matching tenant context")
    void shouldGenerateAndDecryptDek() {
        KeyContext contextAlpha = KeyContext.forTenant(tenantAlpha);

        try (GeneratedDataKey dataKey = applianceKms.generateDataKey(tenantAlpha, keyId, contextAlpha)) {
            assertThat(dataKey.wrappedDek().length()).isGreaterThan(32);
            assertThat(dataKey.plaintextDek().length()).isEqualTo(32);

            try (SensitiveKeyMaterial unwrapped = applianceKms.decryptDataKey(tenantAlpha, keyId, dataKey.wrappedDek(), contextAlpha)) {
                assertThat(unwrapped.getEncoded()).isEqualTo(dataKey.plaintextDek().getEncoded());
            }
        }
    }

    @Test
    @DisplayName("Assert unwrapping DEK under different tenant context fails (Cross-Tenant Isolation I-ENV-002)")
    void shouldRejectCrossTenantUnwrap() {
        KeyContext contextAlpha = KeyContext.forTenant(tenantAlpha);
        KeyContext contextBeta = KeyContext.forTenant(tenantBeta);

        GeneratedDataKey dataKey = applianceKms.generateDataKey(tenantAlpha, keyId, contextAlpha);

        // Attacker attempts to unwrap tenantAlpha's DEK using tenantBeta context
        assertThatThrownBy(() -> applianceKms.decryptDataKey(tenantBeta, keyId, dataKey.wrappedDek(), contextBeta))
                .isInstanceOf(KeyManagementUnavailableException.class)
                .hasMessageContaining("Failed to unwrap DEK");
    }

    @Test
    @DisplayName("Assert master KEK PEM export and re-import preserves unwrap capability")
    void shouldExportAndImportMasterKekPem() {
        String pem = applianceKms.exportMasterKekPem();
        assertThat(pem).startsWith("-----BEGIN AES KEY-----");
        assertThat(pem).endsWith("-----END AES KEY-----\n");

        LocalApplianceKeyManagementClient reimportedKms = LocalApplianceKeyManagementClient.fromPem(pem);

        KeyContext context = KeyContext.forTenant(tenantAlpha);
        GeneratedDataKey dataKey = applianceKms.generateDataKey(tenantAlpha, keyId, context);

        // Reimported KMS can unwrap DEK wrapped by original instance
        try (SensitiveKeyMaterial unwrapped = reimportedKms.decryptDataKey(tenantAlpha, keyId, dataKey.wrappedDek(), context)) {
            assertThat(unwrapped.getEncoded()).isEqualTo(dataKey.plaintextDek().getEncoded());
        }
    }

    @Test
    @DisplayName("Assert tampered PEM throws IllegalArgumentException on import")
    void shouldRejectTamperedPem() {
        String invalidPem = "-----BEGIN AES KEY-----\nINVALIDBASE64$$$\n-----END AES KEY-----\n";
        assertThatThrownBy(() -> LocalApplianceKeyManagementClient.fromPem(invalidPem))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
