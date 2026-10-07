package br.com.wallet.intelligence.internal.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SpendingEventListener Unit Tests (Phase 3.0 Module Foundation)")
class SpendingEventListenerTest {

    private SpendingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new SpendingEventListener();
    }

    @Nested
    @DisplayName("Tenant Validation & Propagation (REQ-INTEL-005, I-INTEL-010)")
    class TenantValidationTests {

        @Test
        @DisplayName("Transfer event with valid tenantId is accepted and processed")
        void shouldAcceptValidTransferTenant() {
            var event = new TransferCompletedEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("150.00"),
                    UUID.randomUUID(),
                    OperationOrigin.USER,
                    "tenant-alpha"
            );

            listener.onTransfer(event);

            assertThat(listener.getProcessedEventCount()).isEqualTo(1);
            assertThat(listener.getLastProcessedTenantId()).isEqualTo("tenant-alpha");
        }

        @Test
        @DisplayName("Withdraw event with valid tenantId is accepted and processed")
        void shouldAcceptValidWithdrawTenant() {
            var event = new WithdrawCompletedEvent(
                    UUID.randomUUID(),
                    new BigDecimal("75.50"),
                    UUID.randomUUID(),
                    "tenant-beta"
            );

            listener.onWithdraw(event);

            assertThat(listener.getProcessedEventCount()).isEqualTo(1);
            assertThat(listener.getLastProcessedTenantId()).isEqualTo("tenant-beta");
        }

        @Test
        @DisplayName("Transfer event with blank tenant throws TenantContextMissingException")
        void shouldRejectBlankTransferTenant() {
            assertThatThrownBy(() -> new TransferCompletedEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("100.00"),
                    UUID.randomUUID(),
                    OperationOrigin.USER,
                    "   "
            )).isInstanceOf(TenantContextMissingException.class);
        }

        @Test
        @DisplayName("Withdraw event with blank tenant throws TenantContextMissingException")
        void shouldRejectBlankWithdrawTenant() {
            assertThatThrownBy(() -> new WithdrawCompletedEvent(
                    UUID.randomUUID(),
                    new BigDecimal("50.00"),
                    UUID.randomUUID(),
                    "   "
            )).isInstanceOf(TenantContextMissingException.class);
        }
    }

    @Nested
    @DisplayName("Canonical Event Idempotency (REQ-INTEL-004, I-INTEL-008)")
    class IdempotencyTests {

        @Test
        @DisplayName("Duplicate transfer eventId/operationId is idempotent no-op")
        void shouldIgnoreDuplicateTransferEventId() {
            UUID opId = UUID.randomUUID();
            var event = new TransferCompletedEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal("200.00"),
                    opId,
                    OperationOrigin.USER,
                    "tenant-alpha"
            );

            listener.onTransfer(event);
            listener.onTransfer(event);

            assertThat(listener.getProcessedEventCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Duplicate withdraw eventId/operationId is idempotent no-op")
        void shouldIgnoreDuplicateWithdrawEventId() {
            UUID opId = UUID.randomUUID();
            var event = new WithdrawCompletedEvent(
                    UUID.randomUUID(),
                    new BigDecimal("80.00"),
                    opId,
                    "tenant-alpha"
            );

            listener.onWithdraw(event);
            listener.onWithdraw(event);

            assertThat(listener.getProcessedEventCount()).isEqualTo(1);
        }
    }
}
