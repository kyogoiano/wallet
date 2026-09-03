package br.com.wallet.fraud.investigation.internal.evidence;

import br.com.wallet.fraud.embeddings.api.ArchetypeMatchingService;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles deterministic InvestigationEvidence from database state and risk dimensions.
 */
@Component
public class InvestigationContextBuilder {

    private final FraudRelationshipStore relationshipStore;
    private final BehavioralFeatureStore featureStore;
    private final ArchetypeMatchingService archetypeMatcher;

    public InvestigationContextBuilder(
        @NonNull final FraudRelationshipStore relationshipStore,
        @NonNull final BehavioralFeatureStore featureStore,
        @NonNull final ArchetypeMatchingService archetypeMatcher
    ) {
        this.relationshipStore = Objects.requireNonNull(relationshipStore, "relationshipStore cannot be null");
        this.featureStore = Objects.requireNonNull(featureStore, "featureStore cannot be null");
        this.archetypeMatcher = Objects.requireNonNull(archetypeMatcher, "archetypeMatcher cannot be null");
    }

    @NonNull
    public InvestigationEvidence buildEvidence(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        Optional<FraudEntity> maybeEntity = relationshipStore.findEntityById(entityId);
        FraudEntity entity = maybeEntity.orElse(null);

        double directRisk = entity != null ? entity.directRisk() : 0.0;
        double graphRisk = entity != null ? entity.graphRisk() : 0.0;
        double propagatedRisk = entity != null ? entity.propagatedRisk() : 0.0;
        double behavioralRisk = entity != null ? entity.behavioralRisk() : 0.0;

        Optional<BehavioralFeatureVector> maybeFeatures = featureStore.findFeatures(entityId);
        BehavioralFeatureVector features = maybeFeatures.orElseGet(() -> BehavioralFeatureVector.inactive(entityId));

        ArchetypeMatch match = archetypeMatcher.findTopArchetypeMatch(features);

        FraudRiskSnapshot snapshot = new FraudRiskSnapshot(
            directRisk,
            graphRisk,
            propagatedRisk,
            behavioralRisk,
            features.featureMagnitude(),
            match.archetypeId(),
            match.directionalSimilarity()
        );

        List<AtomicEvidenceItem> items = new ArrayList<>();

        if (!"NONE".equals(match.archetypeId()) && match.directionalSimilarity() > 0.0) {
            items.add(new AtomicEvidenceItem(
                "ARCHETYPE-001",
                "BEHAVIORAL_ARCHETYPE_MATCH",
                entityId.toString(),
                Map.of(
                    "archetype", match.archetypeId(),
                    "similarity", match.directionalSimilarity(),
                    "intensity", features.featureMagnitude(),
                    "transactionCount", features.transactionCount()
                )
            ));
        }

        if (graphRisk > 0.0) {
            items.add(new AtomicEvidenceItem(
                "GRAPH-001",
                "SHARED_INFRASTRUCTURE_RISK",
                entityId.toString(),
                Map.of("graphRisk", graphRisk)
            ));
        }

        if (propagatedRisk > 0.0) {
            items.add(new AtomicEvidenceItem(
                "TEMPORAL-001",
                "TEMPORAL_PATH_EXPOSURE",
                entityId.toString(),
                Map.of("propagatedRisk", propagatedRisk)
            ));
        }

        return new InvestigationEvidence(snapshot, items);
    }
}
