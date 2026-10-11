package br.com.wallet.fraud.embeddings.spi;

import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI for persisting and retrieving behavioral feature vectors and updated risk scores.
 */
public interface BehavioralFeatureStore {

    void upsertFeatures(@NonNull BehavioralFeatureVector vector);

    @NonNull
    Optional<BehavioralFeatureVector> findFeatures(@NonNull UUID entityId);

    void updateBehavioralRisk(@NonNull UUID entityId, double behavioralRisk);
}
