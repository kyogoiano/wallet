package br.com.wallet.ledger.api.guard;

import br.com.wallet.ledger.api.domain.FraudCheckable;
import br.com.wallet.ledger.api.event.FraudEvent;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.core.context.FraudContext;
import br.com.wallet.fraud.fusion.api.FraudGate;
import br.com.wallet.fraud.fusion.api.model.GateAuthorizationResult;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
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
    private final FraudGate fraudGate;

    public FraudCheckHelper(
            final FraudService fraudService,
            final OutboxDao<FraudEvent> outboxDao,
            final AccountDao accountDao,
            final Clock clock,
            final FraudGate fraudGate
    ) {
        this.fraudService = Objects.requireNonNull(fraudService, "fraudService cannot be null");
        this.outboxDao = Objects.requireNonNull(outboxDao, "outboxDao cannot be null");
        this.accountDao = Objects.requireNonNull(accountDao, "accountDao cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.fraudGate = Objects.requireNonNull(fraudGate, "fraudGate cannot be null");
    }

    public void performFraudCheck(@NonNull final FraudCheckable operation) {
        // 1. Evaluate Fraud Gate V4 (P99 < 2ms fused risk profile from DragonflyDB hot cache)
        if (operation.sourceUserIdForFraudCheck() != null) {
            final RiskSubject subject = new RiskSubject(RiskSubjectType.USER, operation.sourceUserIdForFraudCheck().toString());
            final GateAuthorizationResult gateResult = fraudGate.authorize(subject, operation.amount());

            if (!gateResult.authorized()) {
                log.warn("Operation blocked by Fraud Gate V4: operationId={}, userId={}, decision={}, reason={}",
                        operation.operationId(), operation.sourceUserIdForFraudCheck(), gateResult.decision(), gateResult.reason());

                if (gateResult.decision() == br.com.wallet.fraud.fusion.api.model.FraudDecision.HARD_BLOCK) {
                    // 1. Persistent block in PostgreSQL accounts table (I-ACCOUNT-001)
                    accountDao.blockAccountByUserId(operation.sourceUserIdForFraudCheck(), gateResult.reason());
                    // 2. Dual-Store Sync in Redis/Dragonfly (I-ACCOUNT-002)
                    fraudService.blockUser(operation.sourceUserIdForFraudCheck());
                }

                throw new FraudBlockedException(operation.operationId(), operation.sourceUserIdForFraudCheck());
            }
        }

        // 2. Evaluate Real-Time Sliding Window & Velocity Counter Rules
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
