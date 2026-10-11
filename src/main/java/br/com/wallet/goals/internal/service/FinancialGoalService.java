package br.com.wallet.goals.internal.service;

import br.com.wallet.goals.api.FinancialGoalUseCase;
import br.com.wallet.goals.api.dto.CreateGoalCommand;
import br.com.wallet.goals.api.dto.GoalResponse;
import br.com.wallet.goals.api.dto.UpdateGoalCommand;
import br.com.wallet.goals.api.model.FinancialGoal;
import br.com.wallet.goals.api.model.GoalStatus;
import br.com.wallet.goals.internal.persistence.FinancialGoalDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class FinancialGoalService implements FinancialGoalUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinancialGoalService.class);
    private final FinancialGoalDao goalDao;

    public FinancialGoalService(@NonNull final FinancialGoalDao goalDao) {
        this.goalDao = Objects.requireNonNull(goalDao, "goalDao cannot be null");
    }

    @Override
    @Transactional
    @NonNull
    public GoalResponse createGoal(@NonNull final CreateGoalCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        final Instant now = Instant.now();
        final FinancialGoal goal = new FinancialGoal(
                UUID.randomUUID(),
                command.userId(),
                command.walletId(),
                command.targetWalletId(),
                command.name(),
                command.targetAmount(),
                command.targetDate(),
                command.priority(),
                GoalStatus.ACTIVE,
                now,
                now
        );

        goalDao.insert(goal);
        log.info("Created financial goal: {} for wallet: {}", goal.id(), goal.walletId());
        return mapToResponse(goal);
    }

    @Override
    @Transactional
    @NonNull
    public GoalResponse updateGoal(@NonNull final UUID goalId, @NonNull final UpdateGoalCommand command) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        final FinancialGoal existing = goalDao.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found with ID: " + goalId));

        final FinancialGoal updated = new FinancialGoal(
                existing.id(),
                existing.userId(),
                existing.walletId(),
                existing.targetWalletId(),
                command.name(),
                command.targetAmount(),
                command.targetDate(),
                command.priority(),
                existing.status(),
                existing.createdAt(),
                Instant.now()
        );

        goalDao.update(updated);
        log.info("Updated financial goal: {}", goalId);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void pauseGoal(@NonNull final UUID goalId) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        final FinancialGoal goal = goalDao.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found with ID: " + goalId));
        goalDao.updateStatus(goal.id(), GoalStatus.PAUSED);
        log.info("Paused financial goal: {}", goalId);
    }

    @Override
    @Transactional
    public void resumeGoal(@NonNull final UUID goalId) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        final FinancialGoal goal = goalDao.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found with ID: " + goalId));
        goalDao.updateStatus(goal.id(), GoalStatus.ACTIVE);
        log.info("Resumed financial goal: {}", goalId);
    }

    @Override
    @Transactional
    public void cancelGoal(@NonNull final UUID goalId) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        final FinancialGoal goal = goalDao.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found with ID: " + goalId));
        goalDao.updateStatus(goal.id(), GoalStatus.CANCELLED);
        log.info("Cancelled financial goal: {}", goalId);
    }

    @Override
    @Transactional
    public void markAchieved(@NonNull final UUID goalId) {
        Objects.requireNonNull(goalId, "goalId cannot be null");
        final FinancialGoal goal = goalDao.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found with ID: " + goalId));
        goalDao.updateStatus(goal.id(), GoalStatus.ACHIEVED);
        log.info("Marked financial goal as achieved: {}", goalId);
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
