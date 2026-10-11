package br.com.wallet.dlq.api;

import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DlqQueryUseCase {

    @NonNull
    Optional<DlqOperationResponse> findById(@NonNull UUID dlqEventId);

    @NonNull
    List<DlqOperationResponse> findOperations(@NonNull DlqQueryFilter filter, int limit, int offset);
}
