package br.com.wallet.ledger.internal.service;

import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class OperationStateService implements OperationStateUseCase, OperationQueryUseCase {

    private final WalletOperationsDao walletOperationsDao;

    public OperationStateService(final WalletOperationsDao walletOperationsDao) {
        this.walletOperationsDao = walletOperationsDao;
    }

    @Override
    public void markOperationCompleted(@NonNull final UUID operationId, @NonNull final String tenantId) {
        walletOperationsDao.completeOperation(operationId, tenantId);
    }

    @Override
    public void markOperationCompleted(@NonNull final UUID operationId) {
        markOperationCompleted(operationId, "tenant-alpha");
    }

    @Override
    public void markOperationFailed(@NonNull final UUID operationId, final String errorMessage, final String failureType, @NonNull final String tenantId) {
        walletOperationsDao.failOperation(operationId, errorMessage, failureType, tenantId);
    }

    @Override
    public void markOperationFailed(@NonNull final UUID operationId, final String errorMessage, final String failureType) {
        markOperationFailed(operationId, errorMessage, failureType, "tenant-alpha");
    }

    @Override
    public Optional<OperationStatusResponse> getOperationStatus(@NonNull final UUID operationId) {
        return walletOperationsDao.findOperation(operationId)
                .map(op -> new OperationStatusResponse(
                        op.id(),
                        op.status(),
                        op.errorMessage(),
                        op.failureType(),
                        op.createdAt(),
                        op.updatedAt()
                ));
    }
}
