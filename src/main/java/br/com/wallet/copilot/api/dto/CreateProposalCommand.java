package br.com.wallet.copilot.api.dto;

import br.com.wallet.copilot.api.model.ProposalType;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;

public record CreateProposalCommand(
        @NonNull UUID walletId,
        @NonNull ProposalType type,
        @NonNull String parametersJson,
        @NonNull String idempotencyKey
) {
    public CreateProposalCommand {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(parametersJson, "parametersJson cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be blank");
        }
        if (parametersJson.isBlank()) {
            throw new IllegalArgumentException("parametersJson cannot be blank");
        }
    }
}
