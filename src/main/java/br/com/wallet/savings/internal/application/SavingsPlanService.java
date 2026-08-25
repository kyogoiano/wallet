package br.com.wallet.savings.internal.application;

import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SavingsPlanService implements SavingsPlanUseCase {

    private final SavingsPlanDao savingsPlanDao;

    public SavingsPlanService(@NonNull final SavingsPlanDao savingsPlanDao) {
        this.savingsPlanDao = Objects.requireNonNull(savingsPlanDao, "savingsPlanDao cannot be null");
    }

    @Override
    @Transactional
    public SavingsPlanDto createPlan(@NonNull final CreateSavingsPlanCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        if (command.sourceWalletId().equals(command.targetWalletId())) {
            throw new IllegalArgumentException("Source and target wallets must be different");
        }

        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();

        List<SavingsRule> domainRules = new ArrayList<>();
        if (command.rules() != null) {
            for (CreateSavingsRuleCommand ruleCmd : command.rules()) {
                domainRules.add(new SavingsRule(
                        UUID.randomUUID(),
                        planId,
                        ruleCmd.ruleType(),
                        ruleCmd.stepAmount(),
                        ruleCmd.percentageRate(),
                        ruleCmd.ceilingThreshold(),
                        true
                ));
            }
        }

        SavingsPlan plan = new SavingsPlan(
                planId,
                command.sourceWalletId(),
                command.targetWalletId(),
                command.minimumRetainedBalance() != null ? command.minimumRetainedBalance() : java.math.BigDecimal.ZERO,
                "ACTIVE",
                domainRules,
                now,
                now
        );

        savingsPlanDao.insert(plan);

        return toDto(plan);
    }

    @Override
    @Transactional
    public void pausePlan(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        savingsPlanDao.updateStatus(planId, "PAUSED");
    }

    @Override
    @Transactional
    public void resumePlan(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        savingsPlanDao.updateStatus(planId, "ACTIVE");
    }

    @Override
    @Transactional
    public void deletePlan(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        savingsPlanDao.delete(planId);
    }

    @Override
    public List<SavingsPlanDto> getPlansForWallet(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return savingsPlanDao.findBySourceWalletId(walletId).stream()
                .map(this::toDto)
                .toList();
    }

    private SavingsPlanDto toDto(SavingsPlan plan) {
        List<SavingsRuleDto> ruleDtos = plan.rules().stream()
                .map(r -> new SavingsRuleDto(
                        r.id(),
                        r.planId(),
                        r.ruleType(),
                        r.stepAmount(),
                        r.percentageRate(),
                        r.ceilingThreshold(),
                        r.isActive()
                ))
                .toList();

        return new SavingsPlanDto(
                plan.id(),
                plan.sourceWalletId(),
                plan.targetWalletId(),
                plan.minimumRetainedBalance(),
                plan.status(),
                ruleDtos,
                plan.createdAt(),
                plan.updatedAt()
        );
    }
}
