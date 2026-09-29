package br.com.wallet.security.replay;

import br.com.wallet.security.envelope.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.5: Two-Phase Nonce Replay Protocol SPI Test (REQ-SEC-028, REQ-SEC-029, I-ENV-004)")
class NonceTrackerTest {

    @Test
    @DisplayName("Assert ReplayKey constructs canonical storage key format")
    void shouldConstructCanonicalRedisStorageKey() {
        TenantId tenantId = new TenantId("tenant-finance");
        PrincipalId principalId = new PrincipalId("client-svc-01");
        String nonce = "nonce-abc-12345";

        ReplayKey key = new ReplayKey(tenantId, principalId, nonce);

        assertThat(key.tenantId()).isEqualTo(tenantId);
        assertThat(key.principalId()).isEqualTo(principalId);
        assertThat(key.nonce()).isEqualTo(nonce);
        assertThat(key.toStorageKey()).isEqualTo("nonce:tenant-finance:client-svc-01:nonce-abc-12345");
    }

    @Test
    @DisplayName("Assert ReplayKey rejects invalid null or blank arguments")
    void shouldRejectInvalidArguments() {
        TenantId tenantId = new TenantId("tenant-1");
        PrincipalId principalId = new PrincipalId("p-1");

        assertThatThrownBy(() -> new ReplayKey(null, principalId, "nonce-1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplayKey(tenantId, null, "nonce-1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplayKey(tenantId, principalId, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplayKey(tenantId, principalId, "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Assert NonceReservation sealed pattern matching covers all branches")
    void shouldSupportSealedPatternMatching() {
        NonceReservation admitted = new NonceReservation.Admitted();
        NonceReservation rejected = new NonceReservation.Rejected(ReplayRejectionReason.DUPLICATE_NONCE);
        NonceReservation unavailable = new NonceReservation.Unavailable(ReplayAvailabilityReason.STORAGE_UNAVAILABLE);

        assertThat(resolveStatus(admitted)).isEqualTo("OK");
        assertThat(resolveStatus(rejected)).isEqualTo("REJECTED: DUPLICATE_NONCE");
        assertThat(resolveStatus(unavailable)).isEqualTo("UNAVAILABLE: STORAGE_UNAVAILABLE");
    }

    private String resolveStatus(NonceReservation reservation) {
        return switch (reservation) {
            case NonceReservation.Admitted a -> "OK";
            case NonceReservation.Rejected r -> "REJECTED: " + r.reason();
            case NonceReservation.Unavailable u -> "UNAVAILABLE: " + u.reason();
        };
    }
}
