package br.com.wallet.core.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMismatchExceptionTest {

    @Test
    @DisplayName("Should initialize with simple message")
    void shouldInitializeWithMessage() {
        TenantMismatchException ex = new TenantMismatchException("Cross-tenant access forbidden");

        assertThat(ex.getMessage()).isEqualTo("Cross-tenant access forbidden");
        assertThat(ex.getExpectedTenantId()).isNull();
        assertThat(ex.getActualTenantId()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("Should initialize with message and tenant IDs")
    void shouldInitializeWithTenantIds() {
        TenantMismatchException ex = new TenantMismatchException(
                "Source account tenant does not match command tenant",
                "tenant-alpha",
                "tenant-bravo"
        );

        assertThat(ex.getMessage()).isEqualTo("Source account tenant does not match command tenant");
        assertThat(ex.getExpectedTenantId()).isEqualTo("tenant-alpha");
        assertThat(ex.getActualTenantId()).isEqualTo("tenant-bravo");
    }

    @Test
    @DisplayName("Should initialize with message and cause")
    void shouldInitializeWithCause() {
        RuntimeException cause = new RuntimeException("Underlying error");
        TenantMismatchException ex = new TenantMismatchException("Tenant mismatch detected", cause);

        assertThat(ex.getMessage()).isEqualTo("Tenant mismatch detected");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
