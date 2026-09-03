package br.com.wallet.fraud.investigation.spi;

import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Pluggable SPI for local language model inference backends (Ollama, vLLM, llama.cpp).
 * Strictly local execution with zero cloud egress (I-VEC-004).
 */
public interface LocalInferenceClient {

    @NonNull
    Optional<InvestigationNarrative> generateNarrative(@NonNull StructuredInferenceRequest request);
}
