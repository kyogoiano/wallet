package br.com.wallet.fraud.embeddings.api;

import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Public API for matching behavioral feature vectors against calibrated archetype centroids.
 */
public interface ArchetypeMatchingService {

    @NonNull
    ArchetypeMatch findTopArchetypeMatch(@NonNull BehavioralFeatureVector vector);

    @NonNull
    List<ArchetypeMatch> matchAllArchetypes(@NonNull BehavioralFeatureVector vector);

    double calculateBehavioralRisk(@NonNull BehavioralFeatureVector vector);
}
