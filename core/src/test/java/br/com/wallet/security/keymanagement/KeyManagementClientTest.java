package br.com.wallet.security.keymanagement;

import br.com.wallet.security.envelope.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.4: KeyContext & KeyManagementClient SPI Test (REQ-SEC-024, I-SEC-011)")
class KeyManagementClientTest {

    @Test
    @DisplayName("Assert KeyContext.forTenant binds tenant_id and key_domain immutably")
    void shouldCreateImmutableKmsEncryptionContext() {
        TenantId tenantId = new TenantId("tenant-finance-1");
        KeyContext context = KeyContext.forTenant(tenantId);

        assertThat(context.tenantId()).isEqualTo(tenantId);
        assertThat(context.contextMap()).containsEntry("tenant_id", "tenant-finance-1");
        assertThat(context.contextMap()).containsEntry("key_domain", "WALLET-ENV-V1");

        // Attempting to modify contextMap throws UnsupportedOperationException
        assertThatThrownBy(() -> context.contextMap().put("hacked", "true"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Assert KeyContext rejects null tenantId")
    void shouldRejectNullTenantId() {
        assertThatThrownBy(() -> KeyContext.forTenant(null))
                .isInstanceOf(NullPointerException.class);
    }
}
