package br.com.wallet.infrastructure.adapter;

import br.com.wallet.edge.api.DurableOperationStateProvider;
import br.com.wallet.edge.api.DurableOperationStatus;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter bridging core ledger OperationQueryUseCase to the Edge's DurableOperationStateProvider SPI (I-EDGE-007).
 */
@Component
public class DurableOperationStateAdapter implements DurableOperationStateProvider {

    private final OperationQueryUseCase operationQueryUseCase;

    public DurableOperationStateAdapter(OperationQueryUseCase operationQueryUseCase) {
        this.operationQueryUseCase = operationQueryUseCase;
    }

    @Override
    public Optional<DurableOperationStatus> findOperationStatus(UUID operationId) {
        return operationQueryUseCase.getOperationStatus(operationId)
                .map(status -> new DurableOperationStatus(
                        status.operationId(),
                        status.status().name(),
                        status.updatedAt(),
                        status.errorMessage() != null ? status.errorMessage() : "State: " + status.status().name()
                ));
    }
}
