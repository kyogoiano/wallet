package br.com.wallet.infrastructure.internal.listener;

import br.com.wallet.ledger.api.event.FraudEvent;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Modulith in-process listener for enriching fraud timelines and feature store memory.
 * Replaces legacy FraudConsumer without NATS broker roundtrips (REQ-STRM-002, I-STREAM-001).
 * Resides in infrastructure.internal.listener to maintain acyclic DAG (infrastructure -> ledger -> fraud).
 */
@Component
public class FraudEventListener {

    private static final Logger log = LoggerFactory.getLogger(FraudEventListener.class);

    private final RedisAsyncCommands<String, String> commands;
    private final FraudProjectionEnricher fraudStateProjectionService;
    // Canonical event/operation idempotency guard (I-STREAM-003, REQ-STRM-005)
    private final Set<UUID> processedOperations = ConcurrentHashMap.newKeySet();

    public FraudEventListener(
            final RedisAsyncCommands<String, String> commands,
            final FraudProjectionEnricher fraudStateProjectionService
    ) {
        this.commands = commands;
        this.fraudStateProjectionService = fraudStateProjectionService;
    }

    @ApplicationModuleListener
    public void onFraudEvent(@NonNull final FraudEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        // Canonical idempotency check
        if (!processedOperations.add(event.operationId())) {
            log.info("Duplicate FraudEvent ignored by FraudEventListener: opId={}", event.operationId());
            return;
        }

        log.info("Processing FraudEvent in FraudEventListener: operationId={}, decision={}",
                event.operationId(), event.decision());

        final String tenantId = !event.tenantId().isBlank() ? event.tenantId() : "tenant-alpha";
        switch (event.decision()) {
            case REVIEW -> {
                if (fraudStateProjectionService != null) {
                    fraudStateProjectionService.processReviewEvent(event, tenantId);
                }
            }

            case BLOCK -> {
                if (fraudStateProjectionService != null) {
                    fraudStateProjectionService.processBlockEvent(event, tenantId);
                }
            }

            case ALLOW -> {
                if (commands != null) {
                    final String timelineKey = "user:" + tenantId + ":" + event.from() + ":tx_timeline";
                    commands.zadd(
                            timelineKey,
                            (double) event.timestamp().toEpochMilli(),
                            event.operationId().toString()
                    );
                    commands.expire(timelineKey, 86400 * 30);
                }
            }

            default -> {
                if (commands != null) {
                    commands.hset(
                            "tx:" + tenantId + ":" + event.operationId(),
                            Map.of(
                                    "amount", event.amount().toString(),
                                    "recipient", event.to() != null ? event.to().toString() : "",
                                    "decision", event.decision().name(),
                                    "risk", String.valueOf(event.riskScore())
                            )
                    );
                    commands.expire(
                            "tx:" + tenantId + ":" + event.operationId(),
                            86400 * 30
                    );
                }
            }
        }
    }
}
