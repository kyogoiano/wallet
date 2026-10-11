package br.com.wallet.copilot.api.dto;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;

public record ApproveProposalCommand(
        @NonNull UUID proposalId
) {
    public ApproveProposalCommand {
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
    }
}
