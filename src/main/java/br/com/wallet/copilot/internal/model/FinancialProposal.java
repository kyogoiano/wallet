package br.com.wallet.copilot.internal.model;

import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FinancialProposal(
        @NonNull UUID id,
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull ProposalType type,
        @NonNull String parametersJson,
        @NonNull String parametersHash,
        @NonNull ProposalStatus status,
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
    public FinancialProposal {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(parametersJson, "parametersJson cannot be null");
        Objects.requireNonNull(parametersHash, "parametersHash cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(executionOperationId, "executionOperationId cannot be null");
        Objects.requireNonNull(createdBy, "createdBy cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isLeaseExpired(Instant now) {
        return status == ProposalStatus.EXECUTING
                && executionLeaseUntil != null
                && now.isAfter(executionLeaseUntil);
    }

    public boolean canBeClaimed(Instant now) {
        return status == ProposalStatus.PROPOSED && !isExpired(now);
    }

    public boolean canBeRejected() {
        return status == ProposalStatus.PROPOSED;
    }

    public ProposalResponse toResponse() {
        return new ProposalResponse(
                id,
                tenantId,
                walletId,
                type,
                status,
                parametersJson,
                idempotencyKey,
                executionOperationId,
                createdBy,
                approvedBy,
                createdAt,
                expiresAt,
                executionClaimedAt,
                executionLeaseUntil,
                approvedAt,
                executedAt,
                executionReference
        );
    }
}
