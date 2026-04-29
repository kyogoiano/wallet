package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.FraudRule;
import br.com.wallet.fraud.domain.RiskScore;
import br.com.wallet.fraud.domain.context.FraudContext;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NewRecipientRule implements FraudRule {

    private final Map<String, Set<String>> recentRecipients = new ConcurrentHashMap<>();

    @Override
    public void evaluate(@NonNull final FraudContext ctx, @NonNull final RiskScore score) {

        final var recipients = 
            recentRecipients.computeIfAbsent(ctx.userId(), k -> ConcurrentHashMap.newKeySet());

        if (!recipients.contains(ctx.targetUserId())) {
            score.add(5);
            recipients.add(ctx.targetUserId());
        }
    }
}