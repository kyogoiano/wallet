package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.api.FraudSignalFusionService;
import br.com.wallet.fraud.fusion.api.RiskFusionEngine;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.MlFeatureVector;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.api.model.RiskAttribution;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.fusion.internal.ml.FraudFeatureMapper;
import br.com.wallet.fraud.fusion.internal.ml.OnnxRiskModelEvaluator;
import br.com.wallet.fraud.fusion.internal.orchestration.InvestigationDispatcher;
import br.com.wallet.fraud.fusion.internal.persistence.RiskProfileStore;
import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FraudSignalFusionService Unit Tests (Full Entity Mapping & Evaluation)")
class FraudSignalFusionServiceTest {

    private RiskFusionEngine fusionEngine;
    private OnnxRiskModelEvaluator onnxEvaluator;
    private FraudFeatureMapper featureMapper;
    private FraudRelationshipStore relationshipStore;
    private RiskProfileStore profileStore;
    private InvestigationDispatcher investigationDispatcher;
    private FraudSignalFusionService service;

    @BeforeEach
    void setUp() {
        fusionEngine = mock(RiskFusionEngine.class);
        onnxEvaluator = mock(OnnxRiskModelEvaluator.class);
        featureMapper = new FraudFeatureMapper();
        relationshipStore = mock(FraudRelationshipStore.class);
        profileStore = mock(RiskProfileStore.class);
        investigationDispatcher = mock(InvestigationDispatcher.class);

        service = new FraudSignalFusionService(
            fusionEngine,
            onnxEvaluator,
            featureMapper,
            relationshipStore,
            profileStore,
            investigationDispatcher
        );
    }

    @ParameterizedTest(name = "EntityType {0} must map exhaustively to a valid RiskSubjectType")
    @EnumSource(EntityType.class)
    @DisplayName("REQ-FUSION-006: Exhaustive EntityType to RiskSubjectType Mapping")
    void shouldExhaustivelyMapAllEntityTypes(EntityType entityType) {
        UUID entityId = UUID.randomUUID();
        FraudEntity entity = new FraudEntity(
            entityId,
            entityType,
            0.1, 0.2, 0.3, 0.4, 0.0,
            Instant.now(), Instant.now(), null
        );

        when(relationshipStore.findEntityById(entityId)).thenReturn(Optional.of(entity));
        when(onnxEvaluator.evaluate(any(MlFeatureVector.class)))
            .thenReturn(new MlRiskResult.Available(0.25, "v1", 100L));

        FraudSignalSet signals = FraudSignalSet.of(0.1, 0.2, 0.4, 0.3, 0.25);
        RiskAttribution attribution = new RiskAttribution("NONE", List.of());
        RiskFusionResult mockResult = new RiskFusionResult(0.35, FraudDecision.ALLOW, signals, attribution);
        when(fusionEngine.fuse(any(), any())).thenReturn(mockResult);

        RiskFusionResult result = service.evaluateEntity(entityId);

        assertThat(result).isNotNull();
        assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);

        // Verify that putProfile was called with the correct mapped RiskSubjectType
        RiskSubjectType expectedType = switch (entityType) {
            case USER -> RiskSubjectType.USER;
            case WALLET -> RiskSubjectType.ACCOUNT;
            case DEVICE -> RiskSubjectType.DEVICE;
            case IP -> RiskSubjectType.IP;
            case PHONE -> RiskSubjectType.PHONE;
            case CARD -> RiskSubjectType.CARD;
        };

        verify(profileStore).putProfile(
            eq(new RiskSubject(expectedType, entityId.toString())),
            any(),
            any()
        );
    }
}
