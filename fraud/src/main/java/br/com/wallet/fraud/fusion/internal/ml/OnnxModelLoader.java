package br.com.wallet.fraud.fusion.internal.ml;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import br.com.wallet.fraud.fusion.api.model.OnnxModelMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Loads embedded ONNX runtime artifacts from classpath (REQ-FUSION-003, I-FUSION-004, I-FUSION-010).
 */
@Component
public class OnnxModelLoader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxModelLoader.class);
    public static final String DEFAULT_MODEL_PATH = "models/fraud_risk_tabular_v1.onnx";

    private final String modelResourcePath;
    private final OnnxModelMetadata metadata;
    private OrtEnvironment env;
    private OrtSession session;
    private boolean available;
    private String unavailableReason;

    public OnnxModelLoader() {
        this(DEFAULT_MODEL_PATH);
    }

    public OnnxModelLoader(@NonNull final String modelResourcePath) {
        this.modelResourcePath = Objects.requireNonNull(modelResourcePath, "modelResourcePath cannot be null");
        this.metadata = new OnnxModelMetadata(
            "fraud_risk_tabular",
            "v1",
            FraudFeatureMapper.FEATURE_VERSION_V1,
            FraudFeatureMapper.CANONICAL_FEATURE_NAMES_V1,
            null
        );
        init();
    }

    private void init() {
        try (final var is = getClass().getClassLoader().getResourceAsStream(modelResourcePath)){
            if (is == null) {
                this.available = false;
                this.unavailableReason = "ONNX_MODEL_UNAVAILABLE: Resource not found at " + modelResourcePath;
                log.warn("ONNX model resource not found: {}", modelResourcePath);
                return;
            }

            byte[] modelBytes = is.readAllBytes();
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
            this.available = true;
            log.info("ONNX model successfully loaded from {}, version={}", modelResourcePath, metadata.modelVersion());
        } catch (Throwable t) {
            this.available = false;
            this.unavailableReason = "ONNX_MODEL_UNAVAILABLE: " + t.getMessage();
            log.warn("Failed to initialize ONNX runtime session: {}", t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    @Nullable
    public String unavailableReason() {
        return unavailableReason;
    }

    @Nullable
    public OrtSession session() {
        return session;
    }

    @Nullable
    public OrtEnvironment env() {
        return env;
    }

    @NonNull
    public OnnxModelMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (env != null) {
                env.close();
            }
        } catch (Exception e) {
            log.warn("Error closing ONNX runtime resources: {}", e.getMessage());
        }
    }
}
