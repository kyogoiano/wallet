package br.com.wallet.fraud.fusion.internal.ml;

/**
 * Thrown when an input feature vector does not match the active ONNX model schema (I-FUSION-008).
 */
public class IncompatibleFeatureSchemaException extends RuntimeException {

    public IncompatibleFeatureSchemaException(String message) {
        super(message);
    }

    public IncompatibleFeatureSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
