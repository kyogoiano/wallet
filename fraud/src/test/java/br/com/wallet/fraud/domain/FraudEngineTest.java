package br.com.wallet.fraud.domain;

import br.com.wallet.core.context.FraudContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FraudEngine Unit Tests")
class FraudEngineTest {

    @Test
    @DisplayName("Should evaluate all rules and aggregate risk score and decision")
    void shouldEvaluateRulesAndAggregateScore() {
        final FraudRule allowRule = ctx -> new RuleResult(RuleType.USER_BLOCK, 0, false);
        final FraudRule blockRule = ctx -> new RuleResult(RuleType.GLOBAL_VELOCITY, 80, true);

        final FraudEngine engine = new FraudEngine(List.of(allowRule, blockRule));
        final FraudContext context = new FraudContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5000, Instant.now());

        final FraudResponse response = engine.evaluate(context);

        assertThat(response.riskScore()).isEqualTo(80);
        assertThat(response.fraudDecision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(response.triggeredRules()).contains(RuleType.GLOBAL_VELOCITY);
    }

    @Test
    @DisplayName("Should approve when score is zero")
    void shouldApproveWhenScoreZero() {
        final FraudRule allowRule1 = ctx -> new RuleResult(RuleType.USER_BLOCK, 0, false);
        final FraudRule allowRule2 = ctx -> new RuleResult(RuleType.GLOBAL_VELOCITY, 0, false);

        final FraudEngine engine = new FraudEngine(List.of(allowRule1, allowRule2));
        final FraudContext context = new FraudContext(UUID.randomUUID(), null, UUID.randomUUID(), 1000, Instant.now());

        final FraudResponse response = engine.evaluate(context);

        assertThat(response.riskScore()).isZero();
        assertThat(response.fraudDecision()).isEqualTo(FraudDecision.ALLOW);
        assertThat(response.triggeredRules()).isEmpty();
    }
}
