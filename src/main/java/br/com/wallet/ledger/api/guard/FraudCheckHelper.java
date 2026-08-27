package br.com.wallet.ledger.api.guard;

import br.com.wallet.ledger.api.domain.FraudCheckable;
import br.com.wallet.ledger.api.event.FraudEvent;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.core.context.FraudContext;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Component
public class FraudCheckHelper {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckHelper.class);
    private final FraudService fraudService;
    private final OutboxDao<FraudEvent> outboxDao;
    private final AccountDao accountDao;
    private final Clock clock;

    public FraudCheckHelper(
            final FraudService fraudService,
            final OutboxDao<FraudEvent> outboxDao,
            final AccountDao accountDao,
            final Clock clock
    ) {
        this.fraudService = Objects.requireNonNull(fraudService, "fraudService cannot be null");
        this.outboxDao = Objects.requireNonNull(outboxDao, "outboxDao cannot be null");
        this.accountDao = Objects.requireNonNull(accountDao, "accountDao cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public void performFraudCheck(@NonNull final FraudCheckable operation) {
        final Instant now = clock.instant();
        final FraudContext fraudContext = new FraudContext(
                operation.sourceUserIdForFraudCheck(),
                operation.targetUserIdForFraudCheck(),
                operation.operationId(),
                FraudContext.toCents(operation.amount()),
                now
        );

        final var fraudResponse = fraudService.check(fraudContext);

        outboxDao.save(new FraudEvent(
                fraudContext.userId(),
                fraudContext.targetUserId(),
                operation.amount(),
                fraudContext.operationId(),
                fraudContext.timestamp(),
                fraudResponse.fraudDecision(),
                fraudResponse.riskScore(),
                fraudResponse.triggeredRules()
        ));

        if (fraudResponse.fraudDecision().equals(FraudDecision.BLOCK)) {
            log.warn("Operation blocked by fraud rules: operationId={}, userId={}", operation.operationId(), operation.sourceUserIdForFraudCheck());

            // 1. Persistent block in PostgreSQL accounts table (I-ACCOUNT-001)
            String reason = "Fraud risk score: " + fraudResponse.riskScore() + ", rules: " + fraudResponse.triggeredRules();
            accountDao.blockAccountByUserId(fraudContext.userId(), reason);

            // 2. Dual-Store Sync in Redis (I-ACCOUNT-002)
            fraudService.blockUser(fraudContext.userId());

            throw new FraudBlockedException(operation.operationId(), fraudContext.userId());
        }
    }
}
