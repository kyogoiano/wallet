package br.com.wallet.application.fraud;

import br.com.wallet.domain.FraudCheckable;
import br.com.wallet.domain.event.FraudEvent;
import br.com.wallet.exceptions.FraudBlockedException;
import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.fraud.domain.context.FraudContext;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class FraudCheckHelper {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckHelper.class);
    private final FraudService fraudService;
    private final OutboxDao outboxDao;
    private final Clock clock;

    public FraudCheckHelper(final FraudService fraudService, final OutboxDao outboxDao, final Clock clock) {
        this.fraudService = fraudService;
        this.outboxDao = outboxDao;
        this.clock = clock;
    }

    public void performFraudCheck(@NonNull final FraudCheckable operation) {
        final Instant now = clock.instant();
        final FraudContext fraudContext = new FraudContext(
                operation.getSourceUserIdForFraudCheck(),
                operation.getTargetUserIdForFraudCheck(),
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
            log.warn("Operation blocked by fraud rules: operationId={}, userId={}", operation.operationId(), operation.getSourceUserIdForFraudCheck());
            throw new FraudBlockedException(operation.operationId(), fraudContext.userId());
        }
    }
}
