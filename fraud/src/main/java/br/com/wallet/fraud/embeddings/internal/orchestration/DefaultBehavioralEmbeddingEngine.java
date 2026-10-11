package br.com.wallet.fraud.embeddings.internal.orchestration;

import br.com.wallet.fraud.embeddings.api.ArchetypeMatchingService;
import br.com.wallet.fraud.embeddings.api.BehavioralEmbeddingEngine;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.extraction.EntityTransactionalMetrics;
import br.com.wallet.fraud.embeddings.internal.extraction.FeatureVectorExtractor;
import br.com.wallet.fraud.embeddings.internal.normalization.FeatureNormalizer;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrator implementing BehavioralEmbeddingEngine: extracts 16-D features,
 * normalizes with magnitude retention, matches archetypes, and persists behavioral risk.
 */
@Service
public class DefaultBehavioralEmbeddingEngine implements BehavioralEmbeddingEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultBehavioralEmbeddingEngine.class);

    private final FeatureVectorExtractor extractor;
    private final FeatureNormalizer normalizer;
    private final BehavioralFeatureStore featureStore;
    private final ArchetypeMatchingService matchingService;

    public DefaultBehavioralEmbeddingEngine(
        @NonNull final FeatureVectorExtractor extractor,
        @NonNull final FeatureNormalizer normalizer,
        @NonNull final BehavioralFeatureStore featureStore,
        @NonNull final ArchetypeMatchingService matchingService
    ) {
        this.extractor = Objects.requireNonNull(extractor, "extractor cannot be null");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer cannot be null");
        this.featureStore = Objects.requireNonNull(featureStore, "featureStore cannot be null");
        this.matchingService = Objects.requireNonNull(matchingService, "matchingService cannot be null");
    }

    @Override
    @NonNull
    public BehavioralFeatureVector extractAndPersist(
        @NonNull final UUID entityId,
        @NonNull final EntityTransactionalMetrics metrics
    ) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(metrics, "metrics cannot be null");

        double[] raw = extractor.extractRawFeatures(metrics);
        BehavioralFeatureVector vector = normalizer.normalize(
            entityId,
            raw,
            metrics.txCount30d(),
            BigDecimal.valueOf(metrics.totalOutgoingVolume())
        );

        featureStore.upsertFeatures(vector);
        log.info("Extracted and persisted behavioral vector for entity: {} (magnitude={})", entityId, vector.featureMagnitude());
        return vector;
    }

    @Override
    @NonNull
    public ArchetypeMatch evaluateAndPersistRisk(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        BehavioralFeatureVector vector = featureStore.findFeatures(entityId)
            .orElseGet(() -> BehavioralFeatureVector.inactive(entityId));

        ArchetypeMatch topMatch = matchingService.findTopArchetypeMatch(vector);
        featureStore.updateBehavioralRisk(entityId, topMatch.weightedSimilarity());

        log.info("Evaluated behavioral risk for entity: {}: topArchetype={}, risk={}", 
            entityId, topMatch.archetypeId(), topMatch.weightedSimilarity());
        return topMatch;
    }
}
