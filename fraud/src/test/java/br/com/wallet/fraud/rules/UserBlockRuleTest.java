package br.com.wallet.fraud.rules;

import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.infrastructure.RedisUserStore;
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
@DisplayName("UserBlockRule Unit Tests")
class UserBlockRuleTest {

    @Mock
    private RedisUserStore redisUserStore;

    @InjectMocks
    private UserBlockRule rule;

    @Test
    @DisplayName("Should trigger when user is in blocked store")
    void shouldTriggerWhenUserIsBlocked() {
        final UUID userId = UUID.randomUUID();
        final FraudContext context = new FraudContext(userId, null, UUID.randomUUID(), 10000, Instant.now());

        when(redisUserStore.isBlocked(userId)).thenReturn(true);

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.scoreImpact()).isEqualTo(30);
        assertThat(result.ruleType()).isEqualTo(RuleType.USER_BLOCK);
    }

    @Test
    @DisplayName("Should not trigger when user is not blocked")
    void shouldNotTriggerWhenUserIsNotBlocked() {
        final UUID userId = UUID.randomUUID();
        final FraudContext context = new FraudContext(userId, null, UUID.randomUUID(), 10000, Instant.now());

        when(redisUserStore.isBlocked(userId)).thenReturn(false);

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreImpact()).isZero();
        assertThat(result.ruleType()).isEqualTo(RuleType.USER_BLOCK);
    }
}
