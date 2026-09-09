package br.com.wallet.fraud.fusion.internal.persistence;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DragonflyDB / Redis Hash implementation of the RiskProfileStore (REQ-FUSION-006, I-FUSION-006).
 */
@Repository
public class RedisRiskProfileStore implements RiskProfileStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRiskProfileStore.class);

    private final RedisAsyncCommands<String, String> redisCommands;

    public RedisRiskProfileStore(@NonNull final RedisAsyncCommands<String, String> redisCommands) {
        this.redisCommands = Objects.requireNonNull(redisCommands, "redisCommands cannot be null");
    }

    @Override
    public void putProfile(@NonNull final RiskSubject subject, @NonNull final RiskProfile profile, @NonNull final Duration ttl) {
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        Objects.requireNonNull(ttl, "ttl cannot be null");

        String key = subject.toKey();
        Map<String, String> fields = new HashMap<>(10);
        fields.put("direct", String.valueOf(profile.direct()));
        fields.put("graph", String.valueOf(profile.graph()));
        fields.put("propagated", String.valueOf(profile.propagated()));
        fields.put("behavioral", String.valueOf(profile.behavioral()));
        fields.put("ml", String.valueOf(profile.ml()));
        fields.put("final", String.valueOf(profile.finalRisk()));
        fields.put("status", profile.status().name());
        fields.put("primary_driver", profile.primaryDriver());
        fields.put("degraded", String.valueOf(profile.degraded()));
        fields.put("updated_at", String.valueOf(profile.updatedAt().toEpochMilli()));

        try {
            redisCommands.hset(key, fields).toCompletableFuture().join();
            redisCommands.expire(key, Math.max(1, ttl.toSeconds())).toCompletableFuture().join();
            log.debug("Cached risk profile for {} with TTL {}s", key, ttl.toSeconds());
        } catch (Exception e) {
            log.error("Failed to cache risk profile for key {}: {}", key, e.getMessage());
        }
    }

    @Override
    @NonNull
    public Optional<RiskProfile> getProfile(@NonNull final RiskSubject subject) {
        Objects.requireNonNull(subject, "subject cannot be null");

        String key = subject.toKey();
        try {
            Map<String, String> map = redisCommands.hgetall(key).toCompletableFuture().join();
            if (map == null || map.isEmpty()) {
                return Optional.empty();
            }

            double direct = Double.parseDouble(map.getOrDefault("direct", "0.0"));
            double graph = Double.parseDouble(map.getOrDefault("graph", "0.0"));
            double propagated = Double.parseDouble(map.getOrDefault("propagated", "0.0"));
            double behavioral = Double.parseDouble(map.getOrDefault("behavioral", "0.0"));
            double ml = Double.parseDouble(map.getOrDefault("ml", "0.0"));
            double finalRisk = Double.parseDouble(map.getOrDefault("final", "0.0"));
            FraudDecision status = FraudDecision.valueOf(map.getOrDefault("status", "ALLOW"));
            String primaryDriver = map.getOrDefault("primary_driver", "NONE");
            boolean degraded = Boolean.parseBoolean(map.getOrDefault("degraded", "false"));
            long updatedAtEpoch = Long.parseLong(map.getOrDefault("updated_at", String.valueOf(System.currentTimeMillis())));
            Instant updatedAt = Instant.ofEpochMilli(updatedAtEpoch);

            return Optional.of(new RiskProfile(
                direct, graph, propagated, behavioral, ml, finalRisk,
                status, primaryDriver, degraded, updatedAt
            ));
        } catch (Exception e) {
            log.warn("Failed retrieving risk profile for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void evictProfile(@NonNull final RiskSubject subject) {
        Objects.requireNonNull(subject, "subject cannot be null");
        try {
            redisCommands.del(subject.toKey()).toCompletableFuture().join();
        } catch (Exception e) {
            log.warn("Failed evicting risk profile for key {}: {}", subject.toKey(), e.getMessage());
        }
    }
}
