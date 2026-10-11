package br.com.wallet.goals.api;

import br.com.wallet.goals.api.dto.GoalStrategyResponse;
import br.com.wallet.goals.api.dto.SimulateGoalCommand;
import br.com.wallet.goals.api.model.MultiGoalStrategyReport;
import org.jspecify.annotations.NonNull;

import java.time.LocalDate;
import java.util.UUID;

public interface GoalStrategyUseCase {
    @NonNull
    GoalStrategyResponse calculateStrategy(@NonNull UUID goalId, @NonNull LocalDate evaluationDate);

    @NonNull
    GoalStrategyResponse simulate(@NonNull SimulateGoalCommand command, @NonNull LocalDate evaluationDate);

    @NonNull
    MultiGoalStrategyReport evaluateWallet(@NonNull UUID walletId, @NonNull LocalDate evaluationDate);
}
