package br.com.wallet.goals.api;

import br.com.wallet.goals.api.dto.GoalResponse;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalQueryUseCase {
    @NonNull
    Optional<GoalResponse> findGoalById(@NonNull UUID goalId);

    @NonNull
    List<GoalResponse> findGoalsByWalletId(@NonNull UUID walletId);
}
