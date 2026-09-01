package br.com.wallet.fraud.intelligence.internal.engine;

import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudFeatureProvider;
import br.com.wallet.fraud.intelligence.domain.FraudGraphQuery;
import br.com.wallet.fraud.intelligence.domain.GraphRiskSignals;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class GraphPatternEngine implements FraudFeatureProvider {

    private static final Logger log = LoggerFactory.getLogger(GraphPatternEngine.class);
    private static final Duration CYCLE_DETECTION_WINDOW = Duration.ofHours(1);
    private static final Duration VELOCITY_WINDOW = Duration.ofHours(24);
    private static final int MAX_CYCLE_HOPS = 4;

    private final FraudGraphQuery graphQuery;

    public GraphPatternEngine(@NonNull final FraudGraphQuery graphQuery) {
        this.graphQuery = Objects.requireNonNull(graphQuery, "graphQuery cannot be null");
    }

    @Override
    @NonNull
    public GraphRiskSignals evaluateGraphSignals(@NonNull final UUID entityId, @NonNull final Instant asOf) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        // 1. Cycle Detection (A -> B -> C -> A)
        List<UUID> cycle = graphQuery.detectCycles(entityId, CYCLE_DETECTION_WINDOW, MAX_CYCLE_HOPS, asOf);
        double cycleRisk = cycle.isEmpty() ? 0.0 : 1.0;
        if (cycleRisk > 0.0) {
            log.warn("Detected circular money loop for entity {}: {}", entityId, cycle);
        }

        // 2. Shared Identity Clustering (Devices, IPs, Phones)
        List<UUID> sharedDevices = graphQuery.findSharedEntities(entityId, EntityType.DEVICE, 1, asOf);
        double sharedIdentityRisk = calculateSharedRisk(sharedDevices.size());

        // 3. Outgoing Counterparty Dispersion (Fan-Out)
        long uniqueCounterparties = graphQuery.countUniqueCounterparties(entityId, VELOCITY_WINDOW, asOf);
        double fanOutRisk = calculateFanOutRisk(uniqueCounterparties);

        // 4. Combined Mule Hub Suspicion
        double muleHubRisk = (cycleRisk * 0.5) + (sharedIdentityRisk * 0.3) + (fanOutRisk * 0.2);

        return new GraphRiskSignals(
            cycleRisk,
            0.0, // fanInRisk evaluated when entity is destination
            fanOutRisk,
            sharedIdentityRisk,
            Math.min(1.0, muleHubRisk)
        );
    }

    private static double calculateSharedRisk(int sharedCount) {
        if (sharedCount == 0) return 0.0;
        if (sharedCount == 1) return 0.35;
        if (sharedCount == 2) return 0.70;
        return 1.0;
    }

    private static double calculateFanOutRisk(long uniqueCounterparties) {
        if (uniqueCounterparties <= 3) return 0.0;
        if (uniqueCounterparties <= 10) return (uniqueCounterparties - 3) * 0.10; // Up to 0.70
        return 1.0;
    }
}
