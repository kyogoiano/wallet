package br.com.wallet.fraud.rules;

import br.com.wallet.fraud.domain.FraudRule; 
import br.com.wallet.fraud.domain.RuleResult;
import br.com.wallet.fraud.domain.RuleType;
import br.com.wallet.fraud.domain.context.FraudContext;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NewRecipientRule implements FraudRule {

    private final Map<UUID, Set<UUID>> recentRecipients = new ConcurrentHashMap<>();

    @Override
    public RuleResult evaluate(@NonNull final FraudContext ctx) {
        boolean triggered = false;
        int score = 0;
        if(ctx.targetUserId() != null) {
            final var recipients =
                    recentRecipients.computeIfAbsent(ctx.userId(), k -> ConcurrentHashMap.newKeySet());

            if (!recipients.contains(ctx.targetUserId())) {
                triggered = true;
                score += 5;
                recipients.add(ctx.targetUserId());
            }
        }
        return  new RuleResult(RuleType.NEW_RECIPIENT, score, triggered);
    }
}