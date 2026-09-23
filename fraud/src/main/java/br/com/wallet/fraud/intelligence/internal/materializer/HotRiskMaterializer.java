package br.com.wallet.fraud.intelligence.internal.materializer;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Component
public class HotRiskMaterializer {

    private static final Logger log = LoggerFactory.getLogger(HotRiskMaterializer.class);
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final RedisAsyncCommands<String, String> redisCommands;

    public HotRiskMaterializer(@NonNull final RedisAsyncCommands<String, String> redisCommands) {
        this.redisCommands = Objects.requireNonNull(redisCommands, "redisCommands cannot be null");
    }

    @NonNull
    public CompletionStage<String> materializeGraphRisk(@NonNull final UUID entityId, final double graphRisk) {
        throw new br.com.wallet.core.exceptions.TenantContextMissingException("Tenant identifier is required for graph risk materialization");
    }

    @NonNull
    public CompletionStage<String> materializeGraphRisk(@NonNull final UUID entityId, final double graphRisk, final String tenantId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        validateCanonicalTenantId(tenantId);
        String key = "risk:" + tenantId + ":user:" + entityId + ":graph_risk";
        String value = String.valueOf(graphRisk);

        log.debug("Materializing graph risk for {} into DragonflyDB: key={}, score={}", entityId, key, value);
        return redisCommands.set(key, value, SetArgs.Builder.ex(DEFAULT_TTL));
    }

    @NonNull
    public CompletionStage<String> materializeTemporalRisk(@NonNull final UUID entityId, final double temporalRisk, final String tenantId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        validateCanonicalTenantId(tenantId);
        String key = "risk:" + tenantId + ":user:" + entityId + ":temporal_risk";
        String value = String.valueOf(temporalRisk);

        log.debug("Materializing temporal risk for {} into DragonflyDB: key={}, score={}", entityId, key, value);
        return redisCommands.set(key, value, SetArgs.Builder.ex(DEFAULT_TTL));
    }

    @NonNull
    public CompletionStage<Double> getHotGraphRisk(@NonNull final UUID entityId) {
        throw new br.com.wallet.core.exceptions.TenantContextMissingException("Tenant identifier is required to get graph risk");
    }

    @NonNull
    public CompletionStage<Double> getHotGraphRisk(@NonNull final UUID entityId, final String tenantId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        validateCanonicalTenantId(tenantId);
        String key = "risk:" + tenantId + ":user:" + entityId + ":graph_risk";

        return redisCommands.get(key).thenApply(val -> {
            if (val == null || val.isBlank()) {
                return 0.0;
            }
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                log.warn("Invalid graph risk value cached for key {}: {}", key, val);
                return 0.0;
            }
        });
    }

    @NonNull
    public CompletionStage<Double> getHotTemporalRisk(@NonNull final UUID entityId, final String tenantId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        validateCanonicalTenantId(tenantId);
        String key = "risk:" + tenantId + ":user:" + entityId + ":temporal_risk";

        return redisCommands.get(key).thenApply(val -> {
            if (val == null || val.isBlank()) {
                return 0.0;
            }
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                log.warn("Invalid temporal risk value cached for key {}: {}", key, val);
                return 0.0;
            }
        });
    }

    private void validateCanonicalTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || tenantId.length() > 64) {
            throw new br.com.wallet.core.exceptions.TenantContextMissingException("Tenant identifier is missing or invalid: " + tenantId);
        }
    }
}
