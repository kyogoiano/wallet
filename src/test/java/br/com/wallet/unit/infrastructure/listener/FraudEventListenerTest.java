package br.com.wallet.unit.infrastructure.listener;

import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.infrastructure.internal.listener.FraudEventListener;
import br.com.wallet.infrastructure.internal.listener.FraudProjectionEnricher;
import br.com.wallet.ledger.api.event.FraudEvent;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudEventListener Unit Tests (REQ-STRM-002, REQ-STRM-005, I-STREAM-003)")
class FraudEventListenerTest {

    @Mock
    private RedisAsyncCommands<String, String> commands;

    @Mock
    private FraudProjectionEnricher enricher;

    private FraudEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new FraudEventListener(commands, enricher);
    }

    @Test
    @DisplayName("REQ-STRM-002: Should enrich timeline on ALLOW decision")
    void shouldEnrichTimelineOnAllowDecision() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEvent event = new FraudEvent(
                userId, null, new BigDecimal("100.00"), operationId, now,
                FraudDecision.ALLOW, 10, List.of(), "tenant-alpha"
        );

        listener.onFraudEvent(event);

        String expectedKey = "user:tenant-alpha:" + userId + ":tx_timeline";
        verify(commands).zadd(eq(expectedKey), eq((double) now.toEpochMilli()), eq(operationId.toString()));
        verify(commands).expire(eq(expectedKey), eq(86400L * 30));
    }

    @Test
    @DisplayName("REQ-STRM-002: Should forward to enricher on REVIEW decision")
    void shouldForwardToEnricherOnReviewDecision() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEvent event = new FraudEvent(
                userId, null, new BigDecimal("250.00"), operationId, now,
                FraudDecision.REVIEW, 60, List.of(RuleType.GLOBAL_VELOCITY), "tenant-alpha"
        );

        listener.onFraudEvent(event);

        verify(enricher).processReviewEvent(eq(event), eq("tenant-alpha"));
    }

    @Test
    @DisplayName("REQ-STRM-002: Should forward to enricher on BLOCK decision")
    void shouldForwardToEnricherOnBlockDecision() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEvent event = new FraudEvent(
                userId, null, new BigDecimal("999.00"), operationId, now,
                FraudDecision.BLOCK, 95, List.of(RuleType.USER_BLOCK), "tenant-alpha"
        );

        listener.onFraudEvent(event);

        verify(enricher).processBlockEvent(eq(event), eq("tenant-alpha"));
    }

    @Test
    @DisplayName("REQ-STRM-005 & I-STREAM-003: Duplicate fraud event must be ignored idempotently")
    void shouldIgnoreDuplicateEventId() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEvent event = new FraudEvent(
                userId, null, new BigDecimal("100.00"), operationId, now,
                FraudDecision.ALLOW, 10, List.of(), "tenant-alpha"
        );

        // First delivery
        listener.onFraudEvent(event);
        verify(commands, times(1)).zadd(anyString(), anyDouble(), anyString());

        // Duplicate delivery
        listener.onFraudEvent(event);
        // Commands still only invoked once
        verify(commands, times(1)).zadd(anyString(), anyDouble(), anyString());
    }
}
