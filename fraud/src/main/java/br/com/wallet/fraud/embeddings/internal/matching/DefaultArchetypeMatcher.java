package br.com.wallet.fraud.embeddings.internal.matching;

import br.com.wallet.fraud.embeddings.api.ArchetypeMatchingService;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.persistence.PostgresArchetypeCentroidDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates exact dot-product similarity against calibrated archetype centroids,
 * enforcing Zero-Activity Neutrality (I-VEC-008) when magnitude is zero.
 */
@Service
public class DefaultArchetypeMatcher implements ArchetypeMatchingService {

    private final PostgresArchetypeCentroidDao centroidDao;

    public DefaultArchetypeMatcher(@NonNull final PostgresArchetypeCentroidDao centroidDao) {
        this.centroidDao = Objects.requireNonNull(centroidDao, "centroidDao cannot be null");
    }

    @Override
    @NonNull
    public ArchetypeMatch findTopArchetypeMatch(@NonNull final BehavioralFeatureVector vector) {
        Objects.requireNonNull(vector, "vector cannot be null");

        // I-VEC-008: Zero Activity Neutrality
        if (vector.featureMagnitude() <= 0.0) {
            return ArchetypeMatch.none();
        }

        List<ArchetypeMatch> allMatches = matchAllArchetypes(vector);
        return allMatches.stream()
            .max(Comparator.comparingDouble(ArchetypeMatch::weightedSimilarity))
            .orElseGet(ArchetypeMatch::none);
    }

    @Override
    @NonNull
    public List<ArchetypeMatch> matchAllArchetypes(@NonNull final BehavioralFeatureVector vector) {
        Objects.requireNonNull(vector, "vector cannot be null");

        // I-VEC-008: Zero Activity Neutrality
        if (vector.featureMagnitude() <= 0.0) {
            return List.of(ArchetypeMatch.none());
        }

        List<PostgresArchetypeCentroidDao.ArchetypeSimilarity> similarities = 
            centroidDao.matchAgainstCentroids(vector.vector());

        return similarities.stream()
            .map(s -> {
                double boundedSim = Math.max(0.0, s.similarity());
                double weighted = boundedSim * s.riskWeight();
                return new ArchetypeMatch(
                    s.archetypeId(),
                    s.similarity(),
                    s.riskWeight(),
                    vector.featureMagnitude(),
                    weighted
                );
            })
            .toList();
    }

    @Override
    public double calculateBehavioralRisk(@NonNull final BehavioralFeatureVector vector) {
        Objects.requireNonNull(vector, "vector cannot be null");
        if (vector.featureMagnitude() <= 0.0) {
            return 0.0;
        }
        return findTopArchetypeMatch(vector).weightedSimilarity();
    }
}
