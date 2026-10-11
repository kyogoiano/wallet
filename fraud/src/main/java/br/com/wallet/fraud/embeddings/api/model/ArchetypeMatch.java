package br.com.wallet.fraud.embeddings.api.model;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Represents the match between an entity's behavioral feature vector and a fraud archetype centroid,
 * exposing both directional similarity (v · c_i) and behavioral intensity (||d||_2) for Phase 0.8 Signal Fusion.
 */
public record ArchetypeMatch(
    @NonNull String archetypeId,
    double directionalSimilarity,
    double archetypeWeight,
    double behavioralIntensity,
    double weightedSimilarity
) {
    public ArchetypeMatch {
        Objects.requireNonNull(archetypeId, "archetypeId cannot be null");
    }

    public static ArchetypeMatch none() {
        return new ArchetypeMatch("NONE", 0.0, 0.0, 0.0, 0.0);
    }
}
