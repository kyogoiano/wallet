package br.com.wallet.goals.internal.service;

import br.com.wallet.goals.api.GoalQueryUseCase;
import br.com.wallet.goals.api.dto.GoalResponse;
import br.com.wallet.goals.api.model.FinancialGoal;
import br.com.wallet.goals.internal.persistence.FinancialGoalDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GoalQueryService implements GoalQueryUseCase {

    private final FinancialGoalDao goalDao;

    public GoalQueryService(@NonNull final FinancialGoalDao goalDao) {
        this.goalDao = Objects.requireNonNull(goalDao, "goalDao cannot be null");
    }

    @Override
    @NonNull
    public Optional<GoalResponse> findGoalById(@NonNull final UUID goalId) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        return goalDao.findById(goalId).map(this::mapToResponse);
    }

    @Override
    @NonNull
    public List<GoalResponse> findGoalsByWalletId(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return goalDao.findByWalletId(walletId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private GoalResponse mapToResponse(final FinancialGoal goal) {
        return new GoalResponse(
                goal.id(),
                goal.userId(),
                goal.walletId(),
                goal.targetWalletId(),
                goal.name(),
                goal.targetAmount(),
                goal.targetDate(),
                goal.priority(),
                goal.status(),
                goal.createdAt(),
                goal.updatedAt()
        );
    }
}
