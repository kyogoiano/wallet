package br.com.wallet.fraud.infrastructure;

import br.com.wallet.core.exceptions.TenantContextMissingException;
import br.com.wallet.fraud.domain.VelocityResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface VelocityStore {
    default VelocityResult checkVelocity(UUID userId, UUID operationId, Instant timestamp) {
        throw new TenantContextMissingException("Tenant identifier is required for velocity check");
    }

    VelocityResult checkVelocity(UUID userId, UUID operationId, Instant timestamp, String tenantId);

    VelocityResult recordTransaction(String tenantId, UUID userId, Instant timestamp, Duration window);
}
