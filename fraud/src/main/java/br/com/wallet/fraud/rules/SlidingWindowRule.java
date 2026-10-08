package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.FraudRule;
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.domain.SlidingAmountWindow;
import br.com.wallet.fraud.domain.context.FraudContext;
import br.com.wallet.fraud.infrasctructure.LocalStateStore;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * bucket overwrite
 * bucket rotation
 * running total
 */
@Component
public class SlidingWindowRule implements FraudRule {

    private final LocalStateStore state;
    // The windowMs is now managed internally by SlidingAmountWindow based on its constructor parameter
    private final static long limit = 10_000_00; // Example limit in cents

    public SlidingWindowRule(final LocalStateStore state) {
        this.state = state;
    }

    @Override
    public RuleResult evaluate(@NonNull final FraudContext ctx) {

        final SlidingAmountWindow window = state.getWindow(ctx.userId());
        final long now = ctx.timestamp().toEpochMilli();
        final long amount = ctx.amountInCents();

        // Add the current transaction to the sliding window
        window.add(now, amount);

        boolean triggered = false;
        int score = 0;

        // Check if the total amount in the window exceeds the limit
        if (window.total() > limit) {
            score += 10;
            triggered = true;
        }

        return new RuleResult(RuleType.SLIDING_WINDOW, score, triggered);
    }
}
