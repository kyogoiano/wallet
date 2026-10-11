package br.com.wallet.dlq.internal.service;

import br.com.wallet.dlq.api.DlqQueryUseCase;
import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DlqQueryService implements DlqQueryUseCase {

    private final DlqOperationsDao dlqDao;

    public DlqQueryService(@NonNull final DlqOperationsDao dlqDao) {
        this.dlqDao = Objects.requireNonNull(dlqDao, "dlqDao cannot be null");
    }

    @Override
    @NonNull
    public Optional<DlqOperationResponse> findById(@NonNull final UUID dlqEventId) {
        Objects.requireNonNull(dlqEventId, "dlqEventId cannot be null");
        return dlqDao.findById(dlqEventId).map(DlqOperationResponse::from);
    }

    @Override
    @NonNull
    public List<DlqOperationResponse> findOperations(@NonNull final DlqQueryFilter filter, final int limit, final int offset) {
        Objects.requireNonNull(filter, "filter cannot be null");
        final int sanitizedLimit = Math.clamp(limit, 1, 100);
        final int sanitizedOffset = Math.max(0, offset);

        return dlqDao.findByFilter(filter, sanitizedLimit, sanitizedOffset)
                .stream()
                .map(DlqOperationResponse::from)
                .toList();
    }
}
