package br.com.wallet.infrastructure.internal.listener;

import br.com.wallet.fraud.intelligence.projector.RelationalGraphProjector;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Modulith in-process listener for projecting completed transfers into the fraud relational graph.
 * Replaces legacy FraudGraphConsumer without broker roundtrips (REQ-STRM-001, I-STREAM-001).
 * Resides in infrastructure.internal.listener to maintain acyclic DAG (infrastructure -> ledger -> fraud).
 */
@Component
public class FraudGraphListener {

    private static final Logger log = LoggerFactory.getLogger(FraudGraphListener.class);

    private final RelationalGraphProjector projector;
    private final AccountUseCase accountUseCase;
    // Canonical event/operation idempotency guard (I-STREAM-003, REQ-STRM-005)
    private final Set<UUID> processedOperations = ConcurrentHashMap.newKeySet();

    public FraudGraphListener(
            @NonNull final RelationalGraphProjector projector,
            @NonNull final AccountUseCase accountUseCase
    ) {
        this.projector = Objects.requireNonNull(projector, "projector cannot be null");
        this.accountUseCase = Objects.requireNonNull(accountUseCase, "accountUseCase cannot be null");
    }

    @ApplicationModuleListener
    public void onTransferCompleted(@NonNull final TransferCompletedEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        // Canonical idempotency check
        if (!processedOperations.add(event.operationId())) {
            log.info("Duplicate TransferCompletedEvent ignored by FraudGraphListener: opId={}", event.operationId());
            return;
        }

        log.info("Processing TransferCompletedEvent in FraudGraphListener: opId={}", event.operationId());

        UUID fromUserId;
        try {
            fromUserId = accountUseCase.find(event.from()).userId();
        } catch (Exception e) {
            log.warn("Could not resolve account for wallet {}: {}", event.from(), e.getMessage());
            fromUserId = event.from();
        }

        UUID toUserId;
        try {
            toUserId = accountUseCase.find(event.to()).userId();
        } catch (Exception e) {
            log.warn("Could not resolve account for wallet {}: {}", event.to(), e.getMessage());
            toUserId = event.to();
        }

        projector.projectTransfer(
                event.from(),
                event.to(),
                fromUserId,
                toUserId,
                event.amount(),
                event.operationId(),
                Instant.now(),
                event.tenantId()
        );
    }
}
