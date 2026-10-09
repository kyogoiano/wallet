package br.com.wallet.copilot.api.event;

import br.com.wallet.copilot.api.model.ProposalType;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public record ProposalCreatedEvent(
        @NonNull UUID proposalId,
        @NonNull String tenantId,
        @NonNull UUID walletId,
        @NonNull ProposalType type,
        @NonNull Instant createdAt
) {
}
