package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public interface OperationQueryUseCase {

    Optional<OperationStatusResponse> getOperationStatus(@NonNull UUID operationId);
}
