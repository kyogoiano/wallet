package br.com.wallet.fraud.fusion.api;

import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.MlFeatureVector;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.api.model.RiskFusionWeights;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.fusion.internal.ml.FraudFeatureMapper;
import br.com.wallet.fraud.fusion.internal.ml.OnnxRiskModelEvaluator;
import br.com.wallet.fraud.fusion.internal.orchestration.InvestigationDispatcher;
import br.com.wallet.fraud.fusion.internal.persistence.RiskProfileStore;
import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * High-level use case service orchestrating feature retrieval, ONNX inference,
 * multi-signal fusion, hot cache materialization, and investigation delegation.
 */
@Service
public class FraudSignalFusionService {

    private static final Logger log = LoggerFactory.getLogger(FraudSignalFusionService.class);
    private static final Duration DEFAULT_PROFILE_TTL = Duration.ofHours(1);

    private final RiskFusionEngine fusionEngine;
    private final OnnxRiskModelEvaluator onnxEvaluator;
    private final FraudFeatureMapper featureMapper;
    private final FraudRelationshipStore relationshipStore;
    private final RiskProfileStore profileStore;
    private final InvestigationDispatcher investigationDispatcher;

    public FraudSignalFusionService(
        @NonNull final RiskFusionEngine fusionEngine,
        @NonNull final OnnxRiskModelEvaluator onnxEvaluator,
        @NonNull final FraudFeatureMapper featureMapper,
        @NonNull final FraudRelationshipStore relationshipStore,
        @NonNull final RiskProfileStore profileStore,
        @NonNull final InvestigationDispatcher investigationDispatcher
    ) {
        this.fusionEngine = Objects.requireNonNull(fusionEngine, "fusionEngine cannot be null");
        this.onnxEvaluator = Objects.requireNonNull(onnxEvaluator, "onnxEvaluator cannot be null");
        this.featureMapper = Objects.requireNonNull(featureMapper, "featureMapper cannot be null");
        this.relationshipStore = Objects.requireNonNull(relationshipStore, "relationshipStore cannot be null");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore cannot be null");
        this.investigationDispatcher = Objects.requireNonNull(investigationDispatcher, "investigationDispatcher cannot be null");
    }

    @NonNull
    public RiskFusionResult evaluateEntity(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        FraudEntity entity = relationshipStore.findEntityById(entityId)
            .orElseGet(() -> FraudEntity.create(entityId, EntityType.USER, Instant.now()));

        // 1. Map features and evaluate ONNX Micro-ML
        MlFeatureVector featureVector = featureMapper.mapToVector(
            entity.directRisk(),
            entity.graphRisk(),
            entity.propagatedRisk(),
            entity.behavioralRisk(),
            100.0,
            1.0
        );
        MlRiskResult mlResult = onnxEvaluator.evaluate(featureVector);

        // 2. Build multi-signal set
        FraudSignalSet signals = new FraudSignalSet(
            entity.directRisk(),
            entity.graphRisk(),
            entity.propagatedRisk(),
            entity.behavioralRisk(),
            mlResult
        );

        // 3. Fused risk calculation & LOO attribution
        RiskFusionResult fusionResult = fusionEngine.fuse(signals, RiskFusionWeights.defaults());

        // 4. Materialize hot state risk profile
        RiskSubjectType subjectType = mapEntityType(entity.entityType());
        RiskSubject subject = new RiskSubject(subjectType, entityId.toString());
        double mlScore = mlResult instanceof MlRiskResult.Available available ? available.score() : 0.0;
        boolean degraded = mlResult instanceof MlRiskResult.Unavailable;

        RiskProfile profile = new RiskProfile(
            signals.directRisk(),
            signals.graphRisk(),
            signals.propagatedRisk(),
            signals.behavioralRisk(),
            mlScore,
            fusionResult.finalRisk(),
            fusionResult.decision(),
            fusionResult.attribution().primaryDriver(),
            degraded,
            Instant.now()
        );
        profileStore.putProfile(subject, profile, DEFAULT_PROFILE_TTL);

        // 5. Dispatch investigation if under REVIEW or RESTRICT
        investigationDispatcher.dispatchIfRequired(entityId, fusionResult);

        return fusionResult;
    }

    private RiskSubjectType mapEntityType(EntityType type) {
        if (type == null) {
            return RiskSubjectType.USER;
        }
        return switch (type) {
            case USER -> RiskSubjectType.USER;
            case WALLET -> RiskSubjectType.ACCOUNT;
            case DEVICE -> RiskSubjectType.DEVICE;
            case IP -> RiskSubjectType.IP;
            case PHONE -> RiskSubjectType.PHONE;
            case CARD -> RiskSubjectType.CARD;
        };
    }
}
