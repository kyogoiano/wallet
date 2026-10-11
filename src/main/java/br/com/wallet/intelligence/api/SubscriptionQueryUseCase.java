package br.com.wallet.intelligence.api;

import br.com.wallet.intelligence.api.dto.SubscriptionResponse;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Public use case for querying active/historical recurring subscriptions (REQ-SUB-008, I-SUB-010).
 */
public interface SubscriptionQueryUseCase {

    @NonNull
    List<SubscriptionResponse> getSubscriptions(
            @NonNull String tenantId,
            @NonNull UUID walletId,
            @Nullable SubscriptionStatus status
    );
}
