package br.com.wallet.fraud.intelligence.internal.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.propagation.PropagatedEntityRisk;
import br.com.wallet.fraud.intelligence.propagation.PropagationConfig;
import br.com.wallet.fraud.intelligence.propagation.PropagationResult;
import br.com.wallet.fraud.intelligence.propagation.RiskPropagationEngine;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of RiskPropagationEngine combining bounded path discovery,
 * exponential half-life decay, and probabilistic union aggregation.
 */
@Service
public class DefaultRiskPropagationEngine implements RiskPropagationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultRiskPropagationEngine.class);

    private final PostgresRiskPropagationDao propagationDao;
    private final PathInfluenceCalculator pathCalculator;
    private final MultiPathAggregator pathAggregator;
    private final PropagationConfig config;

    public DefaultRiskPropagationEngine(
        @NonNull final PostgresRiskPropagationDao propagationDao,
        @NonNull final PathInfluenceCalculator pathCalculator,
        @NonNull final MultiPathAggregator pathAggregator,
        @NonNull final PropagationConfig config
    ) {
        this.propagationDao = Objects.requireNonNull(propagationDao, "propagationDao cannot be null");
        this.pathCalculator = Objects.requireNonNull(pathCalculator, "pathCalculator cannot be null");
        this.pathAggregator = Objects.requireNonNull(pathAggregator, "pathAggregator cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    @Override
    @Transactional
    @NonNull
    public PropagationResult evaluateEntity(@NonNull UUID sourceEntityId, @NonNull Instant asOf) {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        double sourceRisk = propagationDao.getDirectRisk(sourceEntityId);
        if (sourceRisk <= 0.0) {
            log.debug("Entity id={} has 0.0 direct risk; skipping outwards propagation", sourceEntityId);
            return new PropagationResult(sourceEntityId, asOf, Collections.emptyMap(), 0, config.modelVersion());
        }

        List<PostgresRiskPropagationDao.DiscoveredPath> paths = propagationDao.findPathsFromSource(
            sourceEntityId,
            asOf,
            config.maxHops(),
            config.maxPaths()
        );

        Map<UUID, List<PostgresRiskPropagationDao.DiscoveredPath>> pathsByTarget = new HashMap<>();
        for (PostgresRiskPropagationDao.DiscoveredPath path : paths) {
            pathsByTarget.computeIfAbsent(path.targetId(), k -> new ArrayList<>()).add(path);
        }

        Map<UUID, PropagatedEntityRisk> resultMap = new HashMap<>();

        for (final Map.Entry<UUID, List<PostgresRiskPropagationDao.DiscoveredPath>> entry : pathsByTarget.entrySet()) {
            UUID targetId = entry.getKey();
            List<PostgresRiskPropagationDao.DiscoveredPath> targetPaths = entry.getValue();

            List<Double> influences = new ArrayList<>(targetPaths.size());
            int minHops = Integer.MAX_VALUE;
            RelationshipType primaryType = RelationshipType.SHARES;
            double maxWeight = -1.0;

            for (final PostgresRiskPropagationDao.DiscoveredPath p : targetPaths) {
                double infl = pathCalculator.calculatePathInfluence(
                    sourceRisk,
                    p.edgeTypes(),
                    p.edgeTimes(),
                    asOf,
                    config
                );
                influences.add(infl);

                if (p.hopCount() < minHops) {
                    minHops = p.hopCount();
                }

                for (RelationshipType rt : p.edgeTypes()) {
                    double weight = config.getWeight(rt);
                    if (weight > maxWeight) {
                        maxWeight = weight;
                        primaryType = rt;
                    }
                }
            }

            double aggregateRisk = pathAggregator.aggregatePathInfluences(influences);
            if (aggregateRisk > 0.0) {
                resultMap.put(targetId, new PropagatedEntityRisk(
                    targetId,
                    aggregateRisk,
                    minHops == Integer.MAX_VALUE ? 1 : minHops,
                    primaryType,
                    asOf,
                    config.modelVersion()
                ));
            }
        }

        propagationDao.persistPropagatedRisks(resultMap);

        log.info("Evaluated risk propagation rooted at source={} asOf={}: discovered {} paths, updated {} targets",
            sourceEntityId, asOf, paths.size(), resultMap.size());

        return new PropagationResult(sourceEntityId, asOf, resultMap, paths.size(), config.modelVersion());
    }

    @Override
    @Transactional
    @NonNull
    public PropagationResult evaluatePath(@NonNull UUID sourceEntityId, @NonNull UUID targetEntityId, @NonNull Instant asOf) {
        Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
        Objects.requireNonNull(targetEntityId, "targetEntityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        double sourceRisk = propagationDao.getDirectRisk(sourceEntityId);
        if (sourceRisk <= 0.0) {
            return new PropagationResult(sourceEntityId, asOf, Collections.emptyMap(), 0, config.modelVersion());
        }

        List<PostgresRiskPropagationDao.DiscoveredPath> paths = propagationDao.findPathsBetween(
            sourceEntityId,
            targetEntityId,
            asOf,
            config.maxHops(),
            config.maxPaths()
        );

        List<Double> influences = new ArrayList<>(paths.size());
        int minHops = Integer.MAX_VALUE;
        RelationshipType primaryType = RelationshipType.SHARES;
        double maxWeight = -1.0;

        for (PostgresRiskPropagationDao.DiscoveredPath p : paths) {
            double infl = pathCalculator.calculatePathInfluence(
                sourceRisk,
                p.edgeTypes(),
                p.edgeTimes(),
                asOf,
                config
            );
            influences.add(infl);

            if (p.hopCount() < minHops) {
                minHops = p.hopCount();
            }

            for (final RelationshipType rt : p.edgeTypes()) {
                double weight = config.getWeight(rt);
                if (weight > maxWeight) {
                    maxWeight = weight;
                    primaryType = rt;
                }
            }
        }

        double aggregateRisk = pathAggregator.aggregatePathInfluences(influences);
        Map<UUID, PropagatedEntityRisk> resultMap = new HashMap<>();
        if (aggregateRisk > 0.0) {
            PropagatedEntityRisk targetRisk = new PropagatedEntityRisk(
                targetEntityId,
                aggregateRisk,
                minHops == Integer.MAX_VALUE ? 1 : minHops,
                primaryType,
                asOf,
                config.modelVersion()
            );
            resultMap.put(targetEntityId, targetRisk);
            propagationDao.persistPropagatedRisks(resultMap);
        }

        return new PropagationResult(sourceEntityId, asOf, resultMap, paths.size(), config.modelVersion());
    }
}
