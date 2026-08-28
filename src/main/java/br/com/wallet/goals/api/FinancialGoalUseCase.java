package br.com.wallet.goals.api;

import br.com.wallet.goals.api.dto.CreateGoalCommand;
import br.com.wallet.goals.api.dto.GoalResponse;
import br.com.wallet.goals.api.dto.UpdateGoalCommand;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public interface FinancialGoalUseCase {
    @NonNull
    GoalResponse createGoal(@NonNull CreateGoalCommand command);

    @NonNull
    GoalResponse updateGoal(@NonNull UUID goalId, @NonNull UpdateGoalCommand command);

    void pauseGoal(@NonNull UUID goalId);

    void resumeGoal(@NonNull UUID goalId);

    void cancelGoal(@NonNull UUID goalId);

    void markAchieved(@NonNull UUID goalId);
}
