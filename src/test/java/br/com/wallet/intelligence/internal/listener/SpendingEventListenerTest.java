package br.com.wallet.intelligence.internal.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.intelligence.api.event.SubscriptionPriceSpikeEvent;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import br.com.wallet.intelligence.internal.engine.RecurrencePatternEngine;
import br.com.wallet.intelligence.internal.persistence.SubscriptionDao;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpendingEventListener Unit Tests (Phase 3, REQ-SUB-001, REQ-SUB-002, I-SUB-008, I-SUB-011)")
class SpendingEventListenerTest {

    @Mock
    private SubscriptionDao subscriptionDao;

    @Mock
    private RecurrencePatternEngine recurrencePatternEngine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SpendingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new SpendingEventListener(subscriptionDao, recurrencePatternEngine, eventPublisher);
    }

    @Nested
    @DisplayName("TASK-3.1.9: Qualifying Outflows & Series Clustering (I-SUB-011, REQ-SUB-001, REQ-SUB-002)")
    class OutflowClusteringTests {

        @Test
        @DisplayName("Positive Canonical: TransferCompletedEvent with USER origin clusters series by (tenant, from, to)")
        void shouldClusterUserTransferEvent() {
            UUID from = UUID.randomUUID();
            UUID to = UUID.randomUUID();
            UUID opId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("100.00");
            String tenantId = "tenant-alpha";

            var event = new TransferCompletedEvent(
                    from, to, amount, opId, OperationOrigin.USER, tenantId
            );

            when(subscriptionDao.tryRecordProcessedEvent(opId, tenantId)).thenReturn(true);
            when(subscriptionDao.findBySeries(tenantId, from, to)).thenReturn(Optional.empty());

            Subscription dummySub = new Subscription(
                    UUID.randomUUID(), tenantId, from, to,
                    Cadence.IRREGULAR, SubscriptionStatus.DISCOVERED, PriceState.NORMAL,
                    "UNKNOWN", amount, amount, new BigDecimal("0.333333"),
                    1, VarianceType.FIXED, null, Instant.now(), Instant.now(), Instant.now()
            );
            when(recurrencePatternEngine.processObservation(
                    eq(Optional.empty()), eq(tenantId), eq(from), eq(to), eq(amount), any(Instant.class), eq(opId)
            )).thenReturn(new RecurrencePatternEngine.SubscriptionEvaluationResult(dummySub, Optional.empty()));

            listener.onTransfer(event);

            verify(subscriptionDao).tryRecordProcessedEvent(opId, tenantId);
            verify(subscriptionDao).findBySeries(tenantId, from, to);
            verify(subscriptionDao).upsert(dummySub);
            verifyNoInteractions(eventPublisher);
            assertThat(listener.getProcessedEventCount()).isEqualTo(1);
            assertThat(listener.getLastProcessedTenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("Boundary / Negative Gate: Transfer with non-USER origin is discarded from subscription clustering")
        void shouldDiscardNonUserTransferEvent() {
            UUID from = UUID.randomUUID();
            UUID to = UUID.randomUUID();
            UUID opId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("100.00");
            String tenantId = "tenant-alpha";

            var event = new TransferCompletedEvent(
                    from, to, amount, opId, OperationOrigin.SYSTEM, tenantId
            );

            listener.onTransfer(event);

            verifyNoInteractions(subscriptionDao);
            verifyNoInteractions(recurrencePatternEngine);
            verifyNoInteractions(eventPublisher);
            assertThat(listener.getProcessedEventCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("I-SUB-011: WithdrawCompletedEvent without canonical counterparty is discarded from subscription clustering")
        void shouldDiscardWithdrawFromSubscriptionClustering() {
            UUID walletId = UUID.randomUUID();
            UUID opId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("50.00");
            String tenantId = "tenant-alpha";

            var event = new WithdrawCompletedEvent(
                    walletId, amount, opId, tenantId
            );

            when(subscriptionDao.tryRecordProcessedEvent(opId, tenantId)).thenReturn(true);

            listener.onWithdraw(event);

            verify(subscriptionDao).tryRecordProcessedEvent(opId, tenantId);
            verify(subscriptionDao, never()).findBySeries(any(), any(), any());
            verify(subscriptionDao, never()).upsert(any());
            verifyNoInteractions(recurrencePatternEngine);
            assertThat(listener.getProcessedEventCount()).isEqualTo(1);
            assertThat(listener.getLastProcessedTenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("Price Spike: When RecurrencePatternEngine returns spikeEvent, it is published to eventPublisher")
        void shouldPublishPriceSpikeEventWhenDetected() {
            UUID from = UUID.randomUUID();
            UUID to = UUID.randomUUID();
            UUID opId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("115.00");
            String tenantId = "tenant-alpha";

            var event = new TransferCompletedEvent(
                    from, to, amount, opId, OperationOrigin.USER, tenantId
            );

            when(subscriptionDao.tryRecordProcessedEvent(opId, tenantId)).thenReturn(true);
            when(subscriptionDao.findBySeries(tenantId, from, to)).thenReturn(Optional.empty());

            Subscription dummySub = new Subscription(
                    UUID.randomUUID(), tenantId, from, to,
                    Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.PRICE_SPIKE_DETECTED,
                    "UNKNOWN", amount, amount, new BigDecimal("1.000000"),
                    4, VarianceType.FIXED, null, Instant.now(), Instant.now(), Instant.now()
            );
            var spikeEvent = new SubscriptionPriceSpikeEvent(
                    dummySub.id(), tenantId, from, to,
                    new BigDecimal("100.00"), amount, new BigDecimal("15.00"), opId, Instant.now()
            );

            when(recurrencePatternEngine.processObservation(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(new RecurrencePatternEngine.SubscriptionEvaluationResult(dummySub, Optional.of(spikeEvent)));

            listener.onTransfer(event);

            verify(subscriptionDao).upsert(dummySub);
            verify(eventPublisher).publishEvent(spikeEvent);
        }
    }

    @Nested
    @DisplayName("Durable Idempotency & Tenant Validation (I-SUB-008, I-SUB-010)")
    class IdempotencyAndTenantTests {

        @Test
        @DisplayName("I-SUB-008: Duplicate eventId rejected by SubscriptionDao is an idempotent no-op")
        void shouldIgnoreDuplicateEventIdIdempotently() {
            UUID opId = UUID.randomUUID();
            String tenantId = "tenant-alpha";

            var event = new TransferCompletedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"),
                    opId, OperationOrigin.USER, tenantId
            );

            when(subscriptionDao.tryRecordProcessedEvent(opId, tenantId)).thenReturn(false);

            listener.onTransfer(event);

            verify(subscriptionDao).tryRecordProcessedEvent(opId, tenantId);
            verify(subscriptionDao, never()).findBySeries(any(), any(), any());
            verify(subscriptionDao, never()).upsert(any());
            assertThat(listener.getProcessedEventCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("I-SUB-010: Blank tenant throws TenantContextMissingException immediately")
        void shouldRejectBlankTenant() {
            assertThatThrownBy(() -> new TransferCompletedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"),
                    UUID.randomUUID(), OperationOrigin.USER, "  "
            )).isInstanceOf(TenantContextMissingException.class);
        }
    }
}
