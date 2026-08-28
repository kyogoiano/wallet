package br.com.wallet.dlq.api.dto;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

public record ReplayExhaustedResult(
        int replayedCount,
        @NonNull List<UUID> operationIds
) {
}
