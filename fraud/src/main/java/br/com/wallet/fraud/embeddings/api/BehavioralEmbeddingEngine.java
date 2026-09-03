package br.com.wallet.fraud.embeddings.api;

import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.extraction.EntityTransactionalMetrics;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Public API for feature vector extraction, normalization, and behavioral risk evaluation.
 */
public interface BehavioralEmbeddingEngine {

    @NonNull
    BehavioralFeatureVector extractAndPersist(@NonNull UUID entityId, @NonNull EntityTransactionalMetrics metrics);

    @NonNull
    ArchetypeMatch evaluateAndPersistRisk(@NonNull UUID entityId);
}
