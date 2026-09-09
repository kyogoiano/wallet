package br.com.wallet.unit.ledger.guard;

import br.com.wallet.core.context.FraudContext;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.ledger.api.domain.FraudCheckable;
import br.com.wallet.ledger.api.event.FraudEvent;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.FraudResponse;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.fusion.api.FraudGate;
import br.com.wallet.fraud.fusion.api.model.GateAuthorizationResult;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudCheckHelperTest {

    @Mock
    private FraudService fraudService;
    @Mock
    private OutboxDao<FraudEvent> outboxDao;
    @Mock
    private AccountDao accountDao;
    @Mock
    private Clock clock;
    @Mock
    private FraudGate fraudGate;

    private FraudCheckHelper fraudCheckHelper;

    private final UUID operationId = UUID.randomUUID();
    private final UUID sourceUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("100.00");
    private final Instant fixedInstant = Instant.now();

    @BeforeEach
    void setUp() {
        fraudCheckHelper = new FraudCheckHelper(fraudService, outboxDao, accountDao, clock, fraudGate);
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(fraudGate.authorize(any(), any())).thenReturn(GateAuthorizationResult.allow("AUTHORIZED"));
    }

    @Test
    @DisplayName("Should perform fraud check and save event when decision is ALLOW")
    void shouldPerformFraudCheckAndSaveEventWhenDecisionIsAllow() {
        // Given
        FraudCheckable operation = mock(FraudCheckable.class);
        when(operation.operationId()).thenReturn(operationId);
        when(operation.amount()).thenReturn(amount);
        when(operation.sourceUserIdForFraudCheck()).thenReturn(sourceUserId);
        when(operation.targetUserIdForFraudCheck()).thenReturn(targetUserId);

        FraudResponse fraudResponse = new FraudResponse(FraudDecision.ALLOW, 0, List.of());
        when(fraudService.check(any(FraudContext.class))).thenReturn(fraudResponse);

        // When
        fraudCheckHelper.performFraudCheck(operation);

        // Then
        verify(fraudService).check(any(FraudContext.class));
        ArgumentCaptor<FraudEvent> fraudEventCaptor = ArgumentCaptor.forClass(FraudEvent.class);
        verify(outboxDao).save(fraudEventCaptor.capture());

        FraudEvent capturedEvent = fraudEventCaptor.getValue();
        assertThat(capturedEvent.operationId()).isEqualTo(operationId);
        assertThat(capturedEvent.decision()).isEqualTo(FraudDecision.ALLOW);
        assertThat(capturedEvent.riskScore()).isZero();
        assertThat(capturedEvent.triggeredRules()).isEmpty();
        assertThat(capturedEvent.amount()).isEqualTo(amount);
        assertThat(capturedEvent.from()).isEqualTo(sourceUserId);
        assertThat(capturedEvent.to()).isEqualTo(targetUserId);
    }

    @Test
    @DisplayName("Should perform fraud check and save event when decision is REVIEW")
    void shouldPerformFraudCheckAndSaveEventWhenDecisionIsReview() {
        // Given
        FraudCheckable operation = mock(FraudCheckable.class);
        when(operation.operationId()).thenReturn(operationId);
        when(operation.amount()).thenReturn(amount);
        when(operation.sourceUserIdForFraudCheck()).thenReturn(sourceUserId);
        when(operation.targetUserIdForFraudCheck()).thenReturn(targetUserId);

        FraudResponse fraudResponse = new FraudResponse(FraudDecision.REVIEW, 50, List.of(RuleType.GLOBAL_VELOCITY));
        when(fraudService.check(any(FraudContext.class))).thenReturn(fraudResponse);

        // When
        fraudCheckHelper.performFraudCheck(operation);

        // Then
        verify(fraudService).check(any(FraudContext.class));
        ArgumentCaptor<FraudEvent> fraudEventCaptor = ArgumentCaptor.forClass(FraudEvent.class);
        verify(outboxDao).save(fraudEventCaptor.capture());

        FraudEvent capturedEvent = fraudEventCaptor.getValue();
        assertThat(capturedEvent.operationId()).isEqualTo(operationId);
        assertThat(capturedEvent.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(capturedEvent.riskScore()).isEqualTo(50);
        assertThat(capturedEvent.triggeredRules()).containsExactly(RuleType.GLOBAL_VELOCITY);
    }

    @Test
    @DisplayName("Should throw FraudBlockedException, persist account block in DB, and sync Redis when decision is BLOCK")
    void shouldThrowFraudBlockedExceptionWhenDecisionIsBlock() {
        // Given
        FraudCheckable operation = mock(FraudCheckable.class);
        when(operation.operationId()).thenReturn(operationId);
        when(operation.amount()).thenReturn(amount);
        when(operation.sourceUserIdForFraudCheck()).thenReturn(sourceUserId);
        when(operation.targetUserIdForFraudCheck()).thenReturn(targetUserId);

        FraudResponse fraudResponse = new FraudResponse(FraudDecision.BLOCK, 100, List.of(RuleType.SLIDING_WINDOW));
        when(fraudService.check(any(FraudContext.class))).thenReturn(fraudResponse);

        // When / Then
        assertThatThrownBy(() -> fraudCheckHelper.performFraudCheck(operation))
                .isInstanceOf(FraudBlockedException.class)
                .hasMessageContaining("Transaction blocked due to high fraud risk")
                .hasFieldOrPropertyWithValue("operationId", operationId)
                .hasFieldOrPropertyWithValue("userId", sourceUserId);

        verify(fraudService).check(any(FraudContext.class));
        verify(outboxDao).save(any(FraudEvent.class));
        verify(accountDao).blockAccountByUserId(eq(sourceUserId), contains("Fraud risk score: 100"));
        verify(fraudService).blockUser(sourceUserId);
    }

    @Test
    @DisplayName("Should create FraudContext correctly")
    void shouldCreateFraudContextCorrectly() {
        // Given
        FraudCheckable operation = mock(FraudCheckable.class);
        when(operation.operationId()).thenReturn(operationId);
        when(operation.amount()).thenReturn(new BigDecimal("123.45"));
        when(operation.sourceUserIdForFraudCheck()).thenReturn(sourceUserId);
        when(operation.targetUserIdForFraudCheck()).thenReturn(targetUserId);

        FraudResponse fraudResponse = new FraudResponse(FraudDecision.ALLOW, 0, List.of());
        when(fraudService.check(any(FraudContext.class))).thenReturn(fraudResponse);

        // When
        fraudCheckHelper.performFraudCheck(operation);

        // Then
        ArgumentCaptor<FraudContext> fraudContextCaptor = ArgumentCaptor.forClass(FraudContext.class);
        verify(fraudService).check(fraudContextCaptor.capture());

        FraudContext capturedContext = fraudContextCaptor.getValue();
        assertThat(capturedContext.userId()).isEqualTo(sourceUserId);
        assertThat(capturedContext.targetUserId()).isEqualTo(targetUserId);
        assertThat(capturedContext.operationId()).isEqualTo(operationId);
        assertThat(capturedContext.amountInCents()).isEqualTo(12345L); // 123.45 * 100
        assertThat(capturedContext.timestamp()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("Should block and persist account block when Fraud Gate V4 returns HARD_BLOCK")
    void shouldBlockOperationWhenFraudGateReturnsHardBlock() {
        // Given
        FraudCheckable operation = mock(FraudCheckable.class);
        when(operation.operationId()).thenReturn(operationId);
        when(operation.amount()).thenReturn(amount);
        when(operation.sourceUserIdForFraudCheck()).thenReturn(sourceUserId);

        when(fraudGate.authorize(any(), any())).thenReturn(
                GateAuthorizationResult.block(br.com.wallet.fraud.fusion.api.model.FraudDecision.HARD_BLOCK, "HARD_BLOCK: Direct rule violated")
        );

        // When / Then
        assertThatThrownBy(() -> fraudCheckHelper.performFraudCheck(operation))
                .isInstanceOf(FraudBlockedException.class)
                .hasFieldOrPropertyWithValue("operationId", operationId)
                .hasFieldOrPropertyWithValue("userId", sourceUserId);

        verify(accountDao).blockAccountByUserId(eq(sourceUserId), contains("Direct rule violated"));
        verify(fraudService).blockUser(sourceUserId);
        verifyNoInteractions(outboxDao); // Legacy rules not evaluated when pre-execution gate blocks
    }

    @Test
    @DisplayName("Should block transaction without DB account block when Fraud Gate V4 returns RESTRICT")
    void shouldBlockOperationWhenFraudGateReturnsRestrict() {
        // Given
        FraudCheckable operation = mock(FraudCheckable.class);
        when(operation.operationId()).thenReturn(operationId);
        when(operation.amount()).thenReturn(amount);
        when(operation.sourceUserIdForFraudCheck()).thenReturn(sourceUserId);

        when(fraudGate.authorize(any(), any())).thenReturn(
                GateAuthorizationResult.block(br.com.wallet.fraud.fusion.api.model.FraudDecision.RESTRICT, "RESTRICT: High fused risk")
        );

        // When / Then
        assertThatThrownBy(() -> fraudCheckHelper.performFraudCheck(operation))
                .isInstanceOf(FraudBlockedException.class)
                .hasFieldOrPropertyWithValue("operationId", operationId)
                .hasFieldOrPropertyWithValue("userId", sourceUserId);

        verifyNoInteractions(accountDao); // RESTRICT does not hard block the account in PostgreSQL
        verify(fraudService, never()).blockUser(any());
        verifyNoInteractions(outboxDao);
    }
}
