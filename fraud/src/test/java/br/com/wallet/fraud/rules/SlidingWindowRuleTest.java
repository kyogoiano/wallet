package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.domain.SlidingAmountWindow;
import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.infrastructure.LocalStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRuleTest {

    @Mock
    private LocalStateStore localStateStore;

    private SlidingWindowRule slidingWindowRule;
    private UUID userId;
    private UUID operationId;
    private Instant timestamp;

    @BeforeEach
    void setUp() {
        slidingWindowRule = new SlidingWindowRule(localStateStore);
        userId = UUID.randomUUID();
        operationId = UUID.randomUUID();
        timestamp = Instant.now();
    }

    @Test
    @DisplayName("Should not trigger rule if total amount is below limit")
    void shouldNotTriggerRuleIfTotalAmountIsBelowLimit() {
        SlidingAmountWindow window = new SlidingAmountWindow(30); // 30 seconds window
        when(localStateStore.getWindow(userId)).thenReturn(window);

        FraudContext ctx = new FraudContext(userId, null, operationId, 5000L, timestamp); // 50 cents
        RuleResult result = slidingWindowRule.evaluate(ctx);

        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreImpact()).isZero();
        assertThat(result.ruleType()).isEqualTo(RuleType.SLIDING_WINDOW);
        assertThat(window.total()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should trigger rule if total amount exceeds limit")
    void shouldTriggerRuleIfTotalAmountExceedsLimit() {
        SlidingAmountWindow window = new SlidingAmountWindow(30); // 30 seconds window
        // Simulate previous transactions pushing the total close to the limit
        window.add(timestamp.minusSeconds(10).toEpochMilli(), 900000L); // 90.00
        when(localStateStore.getWindow(userId)).thenReturn(window);

        FraudContext ctx = new FraudContext(userId, null, operationId, 150000L, timestamp); // 15.00
        RuleResult result = slidingWindowRule.evaluate(ctx);

        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreImpact()).isEqualTo(10);
        assertThat(result.ruleType()).isEqualTo(RuleType.SLIDING_WINDOW);
        assertThat(window.total()).isGreaterThan(1000000L); // 900000 + 150000 = 1050000
    }

    @Test
    @DisplayName("Should handle multiple evaluations correctly")
    void shouldHandleMultipleEvaluationsCorrectly() {
        SlidingAmountWindow window = new SlidingAmountWindow(30);
        when(localStateStore.getWindow(userId)).thenReturn(window);

        // First transaction: below limit
        FraudContext ctx1 = new FraudContext(userId, null, operationId, 500000L, timestamp); // 5.00
        RuleResult result1 = slidingWindowRule.evaluate(ctx1);
        assertThat(result1.triggered()).isFalse();
        assertThat(window.total()).isEqualTo(500000L);

        // Second transaction: still below limit
        FraudContext ctx2 = new FraudContext(userId, null, UUID.randomUUID(), 300000L, timestamp.plusSeconds(5)); // 3.00
        RuleResult result2 = slidingWindowRule.evaluate(ctx2);
        assertThat(result2.triggered()).isFalse();
        assertThat(window.total()).isEqualTo(800000L);

        // Third transaction: exceeds limit
        FraudContext ctx3 = new FraudContext(userId, null, UUID.randomUUID(), 250000L, timestamp.plusSeconds(10)); // 2.50
        RuleResult result3 = slidingWindowRule.evaluate(ctx3);
        assertThat(result3.triggered()).isTrue();
        assertThat(result3.scoreImpact()).isEqualTo(10);
        assertThat(window.total()).isEqualTo(1050000L);
    }

    @Test
    @DisplayName("Should consider amounts expiring from the window")
    void shouldConsiderAmountsExpiringFromTheWindow() throws InterruptedException {
        SlidingAmountWindow window = new SlidingAmountWindow(1); // 1-second window for easy testing
        when(localStateStore.getWindow(userId)).thenReturn(window);

        // Add amount that will expire
        FraudContext ctx1 = new FraudContext(userId, null, operationId, 900000L, timestamp); // 9.00
        slidingWindowRule.evaluate(ctx1);
        assertThat(window.total()).isEqualTo(900000L);

        // Wait for window to expire
        Thread.sleep(1500); // Sleep for 1.5 seconds

        // Add new amount, should not trigger as old amount expired
        FraudContext ctx2 = new FraudContext(userId, null, UUID.randomUUID(), 150000L, timestamp.plusSeconds(2)); // 1.50
        RuleResult result2 = slidingWindowRule.evaluate(ctx2);
        assertThat(result2.triggered()).isFalse();
        assertThat(window.total()).isEqualTo(150000L); // Only the new amount should be present
    }
}
