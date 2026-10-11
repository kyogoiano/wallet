package br.com.wallet.intelligence.api;

import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Public use case for querying forward cashflow projections (REQ-CASH-004, I-CASH-006).
 */
public interface CashflowQueryUseCase {

    @NonNull
    CashflowProjectionResponse getProjections(
            @NonNull String tenantId,
            @NonNull UUID walletId
    );
}
