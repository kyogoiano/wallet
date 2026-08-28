package br.com.wallet.dlq.api;

import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.model.DlqEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface DlqManagementUseCase {

    void recordDlqEvent(@NonNull DlqEvent dlqEvent);

    @NonNull
    DlqOperationResponse replayOperation(@NonNull UUID dlqEventId);

    @NonNull
    DlqOperationResponse discardOperation(@NonNull UUID dlqEventId, @Nullable String reason);

    @NonNull
    ReplayExhaustedResult replayAllExhausted();
}
