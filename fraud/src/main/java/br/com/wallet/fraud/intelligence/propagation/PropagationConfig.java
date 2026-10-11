package br.com.wallet.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration parameters for the Fraud Risk Propagation & Temporal Decay Engine.
 */
public class PropagationConfig {
    private final @NonNull Duration halfLife;
    private final @NonNull Map<RelationshipType, Double> edgeWeights;
    private final int maxHops;
    private final int maxEntities;
    private final int maxPaths;
    private final double alertThreshold;
    private final @NonNull String modelVersion;

    public PropagationConfig(@NonNull Duration halfLife, @NonNull Map<RelationshipType, Double> edgeWeights, int maxHops, int maxEntities, int maxPaths, double alertThreshold, @NonNull String modelVersion) {
        Objects.requireNonNull(halfLife, "halfLife cannot be null");
        Objects.requireNonNull(edgeWeights, "edgeWeights cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");
        edgeWeights = Collections.unmodifiableMap(new EnumMap<>(edgeWeights));
        this.halfLife = halfLife;
        this.edgeWeights = edgeWeights;
        this.maxHops = maxHops;
        this.maxEntities = maxEntities;
        this.maxPaths = maxPaths;
        this.alertThreshold = alertThreshold;
        this.modelVersion = modelVersion;
    }

    public double getWeight(@NonNull RelationshipType type) {
        Objects.requireNonNull(type, "type cannot be null");
        return edgeWeights.getOrDefault(type, 0.10);
    }

    public static PropagationConfig defaultConfig() {
        Map<RelationshipType, Double> weights = new EnumMap<>(RelationshipType.class);
        weights.put(RelationshipType.OWNS, 0.95);
        weights.put(RelationshipType.SHARED_DEVICE, 0.90);
        weights.put(RelationshipType.SHARED_PHONE, 0.85);
        weights.put(RelationshipType.SHARED_EMAIL, 0.75);
        weights.put(RelationshipType.TRANSFERRED_TO, 0.60);
        weights.put(RelationshipType.USES, 0.50);
        weights.put(RelationshipType.SHARED_IP, 0.35);
        weights.put(RelationshipType.LOGGED_FROM, 0.10);
        weights.put(RelationshipType.SHARES, 0.10);

        return new PropagationConfig(
                Duration.ofDays(7),
                weights,
                3,      // maxHops
                1000,   // maxEntities
                5000,   // maxPaths
                0.60,   // alertThreshold
                "v1"    // modelVersion
        );
    }

    public @NonNull Duration halfLife() {
        return halfLife;
    }

    public @NonNull Map<RelationshipType, Double> edgeWeights() {
        return edgeWeights;
    }

    public int maxHops() {
        return maxHops;
    }

    public int maxEntities() {
        return maxEntities;
    }

    public int maxPaths() {
        return maxPaths;
    }

    public double alertThreshold() {
        return alertThreshold;
    }

    public @NonNull String modelVersion() {
        return modelVersion;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PropagationConfig) obj;
        return Objects.equals(this.halfLife, that.halfLife) &&
                Objects.equals(this.edgeWeights, that.edgeWeights) &&
                this.maxHops == that.maxHops &&
                this.maxEntities == that.maxEntities &&
                this.maxPaths == that.maxPaths &&
                Double.doubleToLongBits(this.alertThreshold) == Double.doubleToLongBits(that.alertThreshold) &&
                Objects.equals(this.modelVersion, that.modelVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(halfLife, edgeWeights, maxHops, maxEntities, maxPaths, alertThreshold, modelVersion);
    }

    @Override
    public String toString() {
        return "PropagationConfig[" +
                "halfLife=" + halfLife + ", " +
                "edgeWeights=" + edgeWeights + ", " +
                "maxHops=" + maxHops + ", " +
                "maxEntities=" + maxEntities + ", " +
                "maxPaths=" + maxPaths + ", " +
                "alertThreshold=" + alertThreshold + ", " +
                "modelVersion=" + modelVersion + ']';
    }

}
