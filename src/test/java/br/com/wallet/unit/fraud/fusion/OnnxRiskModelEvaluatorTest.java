package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.MlFeatureVector;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.internal.ml.FraudFeatureMapper;
import br.com.wallet.fraud.fusion.internal.ml.IncompatibleFeatureSchemaException;
import br.com.wallet.fraud.fusion.internal.ml.OnnxModelLoader;
import br.com.wallet.fraud.fusion.internal.ml.OnnxRiskModelEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OnnxRiskModelEvaluator Unit Tests (REQ-FUSION-003, I-FUSION-008, I-FUSION-010)")
class OnnxRiskModelEvaluatorTest {

    private OnnxRiskModelEvaluator evaluator;
    private OnnxModelLoader modelLoader;

    @BeforeEach
    void setUp() {
        modelLoader = new OnnxModelLoader();
        evaluator = new OnnxRiskModelEvaluator(modelLoader);
    }

    @Test
    @DisplayName("REQ-FUSION-003: Tabular Feature Scoring — Evaluates valid 6-feature vector returning calibrated risk score")
    void shouldEvaluateTabularFeatures() {
        MlFeatureVector validFeatures = new MlFeatureVector(
            1,
            FraudFeatureMapper.CANONICAL_FEATURE_NAMES_V1,
            new float[]{0.20f, 0.80f, 0.50f, 0.30f, 1500.0f, 12.0f}
        );

        MlRiskResult result = evaluator.evaluate(validFeatures);

        assertThat(result).isInstanceOf(MlRiskResult.Available.class);
        MlRiskResult.Available available = (MlRiskResult.Available) result;
        assertThat(available.score()).isBetween(0.0, 1.0);
        assertThat(available.modelVersion()).isEqualTo("v1");
        assertThat(available.inferenceNanos()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("I-FUSION-008: Feature Contract Versioning — Throws IncompatibleFeatureSchemaException on version mismatch")
    void shouldEnforceFeatureSchemaVersionMatch() {
        MlFeatureVector wrongVersion = new MlFeatureVector(
            999, // Incompatible version
            FraudFeatureMapper.CANONICAL_FEATURE_NAMES_V1,
            new float[]{0.1f, 0.2f, 0.3f, 0.4f, 100.0f, 2.0f}
        );

        assertThatThrownBy(() -> evaluator.evaluate(wrongVersion))
            .isInstanceOf(IncompatibleFeatureSchemaException.class)
            .hasMessageContaining("version mismatch");
    }

    @Test
    @DisplayName("I-FUSION-008: Dimension Validation — Throws IncompatibleFeatureSchemaException on feature count mismatch")
    void shouldEnforceFeatureCountMatch() {
        MlFeatureVector incompleteFeatures = new MlFeatureVector(
            1,
            List.of("direct_risk", "graph_risk"),
            new float[]{0.1f, 0.2f}
        );

        assertThatThrownBy(() -> evaluator.evaluate(incompleteFeatures))
            .isInstanceOf(IncompatibleFeatureSchemaException.class)
            .hasMessageContaining("Feature count mismatch");
    }

    @Test
    @DisplayName("I-FUSION-010: Observable Degradation — Returns Unavailable when model artifact is missing or invalid")
    void shouldGracefullyReturnUnavailableWhenModelMissing() {
        OnnxModelLoader brokenLoader = new OnnxModelLoader("non_existent_path.onnx");
        OnnxRiskModelEvaluator brokenEvaluator = new OnnxRiskModelEvaluator(brokenLoader);

        MlFeatureVector validFeatures = new MlFeatureVector(
            1,
            FraudFeatureMapper.CANONICAL_FEATURE_NAMES_V1,
            new float[]{0.2f, 0.3f, 0.4f, 0.5f, 50.0f, 1.0f}
        );

        MlRiskResult result = brokenEvaluator.evaluate(validFeatures);

        assertThat(result).isInstanceOf(MlRiskResult.Unavailable.class);
        MlRiskResult.Unavailable unavailable = (MlRiskResult.Unavailable) result;
        assertThat(unavailable.reason()).contains("ONNX_MODEL_UNAVAILABLE");
    }
}
