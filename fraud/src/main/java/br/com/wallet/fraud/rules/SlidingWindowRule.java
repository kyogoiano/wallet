package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.FraudRule;
import br.com.wallet.fraud.domain.RiskScore;
import br.com.wallet.fraud.domain.SlidingWindow;
import br.com.wallet.fraud.domain.context.FraudContext;
import br.com.wallet.fraud.infrasctructure.LocalStateStore;
import org.jspecify.annotations.NonNull;

public class SlidingWindowRule implements FraudRule {

    private final LocalStateStore state;
    private final long windowMs = 30_000;
    private final long limit = 10_000_00;

    public SlidingWindowRule(final LocalStateStore state) {
        this.state = state;
    }

    @Override
    public void evaluate(@NonNull final FraudContext ctx, @NonNull final RiskScore score) {

        final var window = state.getWindow(ctx.userId());
        final long now = ctx.timestamp().toEpochMilli();
        final long windowStart = now - windowMs;

        synchronized (window) {
            var amount = ctx.amountInCents();
            window.queue.addLast(new SlidingWindow.TransactionEntry(now, amount));
            window.total += amount;

            // ➖ remove expirados
            while (!window.queue.isEmpty() &&
                    window.queue.peekFirst().timestamp() < windowStart) {

                final var expired = window.queue.pollFirst();
                window.total -= expired.amount();
            }

            // ✅ O(1)
            if (window.total > limit) {
                score.add(10);
            }
        }
    }
}