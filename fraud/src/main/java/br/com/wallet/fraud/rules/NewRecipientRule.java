package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.*;
import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.infrastructure.NewRecipientStore;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class NewRecipientRule implements FraudRule {

    private final NewRecipientStore newRecipientStore;

    public NewRecipientRule(NewRecipientStore newRecipientStore) {
        this.newRecipientStore = newRecipientStore;
    }

    @Override
    public RuleResult evaluate(@NonNull final FraudContext ctx) {

        if (ctx.targetUserId() == null) {
            return new RuleResult(RuleType.NEW_RECIPIENT, 0, false);
        }

        return newRecipientStore
                .checkNewRecipient(
                        ctx.userId(),
                        ctx.targetUserId(),
                        ctx.timestamp()
                )
                .thenApply(result ->
                         switch (result) {
                            case RecipientRisk.Ring ring -> {
                                int score =
                                        (normalize(ring.recipientCount(), 20) * 2)
                                                + (normalize(ring.senderCount(), 20) * 3);
                                yield new RuleResult(RuleType.NEW_RECIPIENT_RING, score, true);
                            }

                            case RecipientRisk.Mule mule -> {
                                int score = Math.min(
                                        mule.senderCount() * 2,
                                        20
                                );
                                yield    new RuleResult(RuleType.NEW_RECIPIENT_MULE, score, true);
                            }

                            case RecipientRisk.FanOut fanOut -> {
                                int score = Math.min(
                                        fanOut.recipientCount(),
                                        15
                                );
                                yield new RuleResult(RuleType.NEW_RECIPIENT_FAN_OUT, score, true);
                            }

                            case RecipientRisk.Normal normal ->
                                    new RuleResult(RuleType.NEW_RECIPIENT, 0, false);
                        }
                )
                .toCompletableFuture()
                .join();
    }

    private int normalize(final long value, final int max) {
        return (int) Math.min(value, max);
    }
}