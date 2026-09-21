package br.com.wallet.fraud.infrastructure;

import br.com.wallet.fraud.domain.VelocityResult;

import java.time.Instant;
import java.util.UUID;

public interface VelocityStore {
    default VelocityResult checkVelocity(UUID userId, UUID operationId, Instant timestamp) {
        return checkVelocity(userId, operationId, timestamp, "default");
    }

    VelocityResult checkVelocity(UUID userId, UUID operationId, Instant timestamp, String tenantId);
}
