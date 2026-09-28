package br.com.wallet.fraud.intelligence.projector;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Contract for asynchronous relational graph projections from domain events.
 */
public interface RelationalGraphProjector {

    void projectTransfer(
        @NonNull UUID sourceWalletId,
        @NonNull UUID targetWalletId,
        @NonNull UUID sourceUserId,
        @NonNull UUID targetUserId,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId,
        @NonNull Instant timestamp,
        @NonNull String tenantId
    );

    default void projectTransfer(
        @NonNull UUID sourceWalletId,
        @NonNull UUID targetWalletId,
        @NonNull UUID sourceUserId,
        @NonNull UUID targetUserId,
        @NonNull BigDecimal amount,
        @NonNull UUID operationId,
        @NonNull Instant timestamp
    ) {
        projectTransfer(sourceWalletId, targetWalletId, sourceUserId, targetUserId, amount, operationId, timestamp, "tenant-alpha");
    }
}
