package br.com.wallet.copilot.api.dto;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;

public record RejectProposalCommand(
        @NonNull UUID proposalId,
        String reason
) {
    public RejectProposalCommand {
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
    }

    public RejectProposalCommand(@NonNull UUID proposalId) {
        this(proposalId, null);
    }
}
