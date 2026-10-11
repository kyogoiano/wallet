package br.com.wallet.fraud.investigation.api.model;

public enum InvestigationGenerationStatus {
    GENERATED,
    GENERATED_WITH_FALLBACK,
    VALIDATION_FAILED,
    INFERENCE_UNAVAILABLE
}
