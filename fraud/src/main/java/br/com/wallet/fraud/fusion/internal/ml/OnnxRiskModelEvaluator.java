package br.com.wallet.fraud.fusion.internal.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;
import br.com.wallet.fraud.fusion.api.model.MlFeatureVector;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.api.model.OnnxModelMetadata;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Pure Java embedded ONNX Micro-ML evaluator with schema contract verification (REQ-FUSION-003, I-FUSION-008, I-FUSION-010).
 */
@Component
public class OnnxRiskModelEvaluator {

    private static final Logger log = LoggerFactory.getLogger(OnnxRiskModelEvaluator.class);

    private final OnnxModelLoader loader;

    public OnnxRiskModelEvaluator(@NonNull final OnnxModelLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader cannot be null");
    }

    @NonNull
    public MlRiskResult evaluate(@NonNull final MlFeatureVector features) {
        Objects.requireNonNull(features, "features cannot be null");

        OnnxModelMetadata metadata = loader.metadata();

        // I-FUSION-008: Strict feature contract version and dimension validation
        if (features.featureVersion() != metadata.featureVersion()) {
            throw new IncompatibleFeatureSchemaException(
                "Feature schema version mismatch: expected " + metadata.featureVersion() + ", got " + features.featureVersion()
            );
        }

        if (features.values().length != metadata.featureNames().size()) {
            throw new IncompatibleFeatureSchemaException(
                "Feature count mismatch: expected " + metadata.featureNames().size() + ", got " + features.values().length
            );
        }

        // I-FUSION-010: Observable degradation when model is unavailable
        if (!loader.isAvailable() || loader.session() == null || loader.env() == null) {
            String reason = loader.unavailableReason() != null ? loader.unavailableReason() : "ONNX_MODEL_UNAVAILABLE";
            return new MlRiskResult.Unavailable(reason);
        }

        long startNanos = System.nanoTime();
        try {
            float[][] inputMatrix = new float[1][features.values().length];
            System.arraycopy(features.values(), 0, inputMatrix[0], 0, features.values().length);

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(loader.env(), inputMatrix)) {
                String inputName = loader.session().getInputNames().iterator().next();
                try (OrtSession.Result result = loader.session().run(Map.of(inputName, inputTensor))) {
                    float[][] outputMatrix = (float[][]) result.get(0).getValue();
                    double rawScore = outputMatrix[0][0];
                    double score = Math.clamp(rawScore, 0.0, 1.0);
                    long elapsedNanos = Math.max(1L, System.nanoTime() - startNanos);

                    return new MlRiskResult.Available(score, metadata.modelVersion(), elapsedNanos);
                }
            }
        } catch (Throwable t) {
            log.warn("ONNX inference failed: {}", t.getMessage());
            return new MlRiskResult.Unavailable("ONNX_INFERENCE_ERROR: " + t.getMessage());
        }
    }
}
