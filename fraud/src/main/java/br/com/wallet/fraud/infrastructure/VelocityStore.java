package br.com.wallet.fraud.infrastructure;

import br.com.wallet.fraud.domain.VelocityResult;

import java.time.Instant;
import java.util.UUID;

public interface VelocityStore {
    VelocityResult checkVelocity(UUID userId, UUID operationId, Instant timestamp);
}
