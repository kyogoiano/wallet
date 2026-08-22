package br.com.wallet.fraud.rules;

import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.domain.VelocityResult;
import br.com.wallet.fraud.infrastructure.RedisVelocityStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalVelocityRule Unit Tests")
class GlobalVelocityRuleTest {

    @Mock
    private RedisVelocityStore redisVelocityStore;

    @InjectMocks
    private GlobalVelocityRule rule;

    @Test
    @DisplayName("Should trigger when velocity threshold is exceeded")
    void shouldTriggerWhenVelocityExceeded() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Instant now = Instant.now();
        final FraudContext context = new FraudContext(userId, null, opId, 5000, now);

        when(redisVelocityStore.checkVelocity(userId, opId, now))
                .thenReturn(new VelocityResult.Exceeded(10));

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreImpact()).isEqualTo(30);
        assertThat(result.ruleType()).isEqualTo(RuleType.GLOBAL_VELOCITY);
    }

    @Test
    @DisplayName("Should not trigger when velocity is OK")
    void shouldNotTriggerWhenVelocityOk() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Instant now = Instant.now();
        final FraudContext context = new FraudContext(userId, null, opId, 5000, now);

        when(redisVelocityStore.checkVelocity(userId, opId, now))
                .thenReturn(new VelocityResult.Ok(3));

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreImpact()).isZero();
    }

    @Test
    @DisplayName("Should not score on replay operation")
    void shouldNotScoreOnReplay() {
        final UUID userId = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final Instant now = Instant.now();
        final FraudContext context = new FraudContext(userId, null, opId, 5000, now);

        when(redisVelocityStore.checkVelocity(userId, opId, now))
                .thenReturn(new VelocityResult.Replay(3));

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreImpact()).isZero();
    }
}
