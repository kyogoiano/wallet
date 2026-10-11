package br.com.wallet.fraud.intelligence.domain;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FraudGraphQuery {
    @NonNull List<UUID> detectCycles(@NonNull UUID startNodeId, @NonNull Duration window, int maxHops, @NonNull Instant asOf);
    @NonNull List<UUID> findSharedEntities(@NonNull UUID entityId, @NonNull EntityType targetType, int maxHops, @NonNull Instant asOf);
    long countUniqueCounterparties(@NonNull UUID sourceId, @NonNull Duration window, @NonNull Instant asOf);
}
