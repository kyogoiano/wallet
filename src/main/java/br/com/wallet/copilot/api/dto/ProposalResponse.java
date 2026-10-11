package br.com.wallet.copilot.api.dto;

import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ProposalResponse(
        @NonNull UUID id,
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull ProposalType type,
        @NonNull ProposalStatus status,
        @NonNull String parametersJson,
        @NonNull String idempotencyKey,
        @NonNull String executionOperationId,
        @NonNull String createdBy,
        String approvedBy,
        @NonNull Instant createdAt,
        @NonNull Instant expiresAt,
        Instant executionClaimedAt,
        Instant executionLeaseUntil,
        Instant approvedAt,
        Instant executedAt,
        String executionReference
) {
}
