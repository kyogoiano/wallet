package br.com.wallet.fraud.rules;

import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.domain.RecipientRisk;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.infrastructure.NewRecipientStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NewRecipientRule Unit Tests")
class NewRecipientRuleTest {

    @Mock
    private NewRecipientStore newRecipientStore;

    @InjectMocks
    private NewRecipientRule rule;

    @Test
    @DisplayName("Should return not triggered when targetUserId is null")
    void shouldReturnNotTriggeredWhenTargetIsNull() {
        final FraudContext context = new FraudContext(UUID.randomUUID(), null, UUID.randomUUID(), 1000, Instant.now());

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreImpact()).isZero();
    }

    @Test
    @DisplayName("Should score ring pattern risk")
    void shouldScoreRingPattern() {
        final UUID userId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();
        final Instant now = Instant.now();
        final FraudContext context = new FraudContext(userId, targetId, UUID.randomUUID(), 1000, now);

        when(newRecipientStore.checkNewRecipient(userId, targetId, now))
                .thenReturn(CompletableFuture.completedFuture(new RecipientRisk.Ring(5, 4)));

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.ruleType()).isEqualTo(RuleType.NEW_RECIPIENT_RING);
        assertThat(result.scoreImpact()).isEqualTo((5 * 3) + (4 * 2));
    }

    @Test
    @DisplayName("Should score mule pattern risk")
    void shouldScoreMulePattern() {
        final UUID userId = UUID.randomUUID();
        final UUID targetId = UUID.randomUUID();
        final Instant now = Instant.now();
        final FraudContext context = new FraudContext(userId, targetId, UUID.randomUUID(), 1000, now);

        when(newRecipientStore.checkNewRecipient(userId, targetId, now))
                .thenReturn(CompletableFuture.completedFuture(new RecipientRisk.Mule(6)));

        final RuleResult result = rule.evaluate(context);

        assertThat(result.triggered()).isTrue();
        assertThat(result.ruleType()).isEqualTo(RuleType.NEW_RECIPIENT_MULE);
        assertThat(result.scoreImpact()).isEqualTo(12);
    }
}
