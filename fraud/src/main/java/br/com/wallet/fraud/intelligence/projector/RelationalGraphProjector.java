package br.com.wallet.fraud.intelligence.projector;

import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudFeatureProvider;
import br.com.wallet.fraud.intelligence.domain.FraudRelationship;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipEvent;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.intelligence.domain.GraphRiskSignals;
import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RelationalGraphProjector {

    private static final Logger log = LoggerFactory.getLogger(RelationalGraphProjector.class);

    private final FraudRelationshipStore store;
    private final FraudFeatureProvider featureProvider;
    private final HotRiskMaterializer materializer;

    public RelationalGraphProjector(
        @NonNull final FraudRelationshipStore store,
        @NonNull final FraudFeatureProvider featureProvider,
        @NonNull final HotRiskMaterializer materializer
    ) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.featureProvider = Objects.requireNonNull(featureProvider, "featureProvider cannot be null");
        this.materializer = Objects.requireNonNull(materializer, "materializer cannot be null");
    }

    @Transactional
    public void projectTransfer(
        @NonNull final UUID sourceWalletId,
        @NonNull final UUID targetWalletId,
        @NonNull final UUID sourceUserId,
        @NonNull final UUID targetUserId,
        @NonNull final BigDecimal amount,
        @NonNull final UUID operationId,
        @NonNull final Instant timestamp
    ) {
        Objects.requireNonNull(sourceWalletId, "sourceWalletId cannot be null");
        Objects.requireNonNull(targetWalletId, "targetWalletId cannot be null");
        Objects.requireNonNull(sourceUserId, "sourceUserId cannot be null");
        Objects.requireNonNull(targetUserId, "targetUserId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");

        log.info("Projecting transfer event: {} -> {}, amount={}, opId={}", sourceWalletId, targetWalletId, amount, operationId);

        // 1. Ensure entities exist
        store.upsertEntity(FraudEntity.create(sourceUserId, EntityType.USER, timestamp));
        store.upsertEntity(FraudEntity.create(targetUserId, EntityType.USER, timestamp));
        store.upsertEntity(FraudEntity.create(sourceWalletId, EntityType.WALLET, timestamp));
        store.upsertEntity(FraudEntity.create(targetWalletId, EntityType.WALLET, timestamp));

        // 2. Record immutable temporal event
        FraudRelationshipEvent event = new FraudRelationshipEvent(
            UUID.randomUUID(),
            sourceWalletId,
            targetWalletId,
            RelationshipType.TRANSFERRED_TO,
            timestamp,
            operationId,
            amount,
            null
        );
        store.recordRelationshipEvent(event);

        // 3. Upsert aggregate relationship edge
        FraudRelationship relationship = new FraudRelationship(
            sourceWalletId,
            targetWalletId,
            RelationshipType.TRANSFERRED_TO,
            timestamp,
            timestamp,
            1,
            amount,
            null
        );
        store.upsertRelationship(relationship);

        // 4. Link user to wallet
        store.upsertRelationship(new FraudRelationship(sourceUserId, sourceWalletId, RelationshipType.OWNS, timestamp, timestamp, 1, BigDecimal.ZERO, null));
        store.upsertRelationship(new FraudRelationship(targetUserId, targetWalletId, RelationshipType.OWNS, timestamp, timestamp, 1, BigDecimal.ZERO, null));

        // 5. Evaluate graph patterns & update scores
        GraphRiskSignals signals = featureProvider.evaluateGraphSignals(sourceWalletId, timestamp);
        double graphScore = signals.calculateCompositeScore();

        store.updateGraphRisk(sourceUserId, graphScore, timestamp);
        store.updateGraphRisk(sourceWalletId, graphScore, timestamp);

        // 6. Materialize hot cache in DragonflyDB
        materializer.materializeGraphRisk(sourceUserId, graphScore);
        materializer.materializeGraphRisk(sourceWalletId, graphScore);
    }
}
