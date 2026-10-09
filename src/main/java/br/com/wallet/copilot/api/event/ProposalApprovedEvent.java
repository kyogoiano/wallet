package br.com.wallet.copilot.api.event;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ProposalApprovedEvent(
        @NonNull UUID proposalId,
        @NonNull String tenantId,
        @NonNull String approvedBy,
        @NonNull Instant approvedAt
) {
}
