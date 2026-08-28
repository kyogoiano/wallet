package br.com.wallet.dlq.api.dto;

import org.jspecify.annotations.Nullable;

public record DiscardDlqCommand(
        @Nullable String reason
) {
}
