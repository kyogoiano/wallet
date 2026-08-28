package br.com.wallet.dlq.api.dto;

import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record DlqQueryFilter(
        @Nullable DlqStatus status,
        @Nullable DlqFailureType failureType,
        @Nullable String eventType,
        @Nullable UUID operationId
) {
}
