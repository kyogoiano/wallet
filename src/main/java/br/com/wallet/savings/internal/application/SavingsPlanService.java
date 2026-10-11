package br.com.wallet.savings.internal.application;

import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
import br.com.wallet.savings.internal.persistence.SavingsRuleDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class SavingsPlanService implements SavingsPlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(SavingsPlanService.class);
    private final SavingsPlanDao savingsPlanDao;
    private final SavingsRuleDao savingsRuleDao;

    public SavingsPlanService(
            @NonNull final SavingsPlanDao savingsPlanDao,
            @NonNull final SavingsRuleDao savingsRuleDao
    ) {
        this.savingsPlanDao = Objects.requireNonNull(savingsPlanDao, "savingsPlanDao cannot be null");
        this.savingsRuleDao = Objects.requireNonNull(savingsRuleDao, "savingsRuleDao cannot be null");
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
                validateRuleCommand(ruleCmd);
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
                command.minimumRetainedBalance() != null ? command.minimumRetainedBalance() : BigDecimal.ZERO,
                "ACTIVE",
                domainRules,
                now,
                now
        );

        savingsPlanDao.insert(plan);

        return toDto(plan);
    }

    @Override
    public SavingsPlanDto getPlan(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        return savingsPlanDao.findById(planId)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("Savings plan not found: " + planId));
    }

    @Override
    public List<SavingsPlanDto> listPlans(final Integer limit, final Integer offset) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 100;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;
        return savingsPlanDao.findAll(effectiveLimit, effectiveOffset).stream()
                .map(this::toDto)
                .toList();
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
    public List<SavingsPlanDto> getPlansBySourceWallet(@NonNull final UUID sourceWalletId) {
        Objects.requireNonNull(sourceWalletId, "sourceWalletId cannot be null");
        return savingsPlanDao.findBySourceWalletId(sourceWalletId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<SavingsPlanDto> getPlansByTargetWallet(@NonNull final UUID targetWalletId) {
        Objects.requireNonNull(targetWalletId, "targetWalletId cannot be null");
        return savingsPlanDao.findByTargetWalletId(targetWalletId).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<SavingsPlanDto> getPlansForWallet(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        var plans = savingsPlanDao.findByWalletId(walletId);
        log.info("Found {} savings plans for wallet requested", plans.size());
        return plans.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public SavingsRuleDto addRule(@NonNull final UUID planId, @NonNull final CreateSavingsRuleCommand command) {
        Objects.requireNonNull(planId, "planId cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        savingsPlanDao.findById(planId)
                .orElseThrow(() -> new NoSuchElementException("Savings plan not found: " + planId));

        validateRuleCommand(command);

        UUID ruleId = UUID.randomUUID();
        SavingsRule rule = new SavingsRule(
                ruleId,
                planId,
                command.ruleType(),
                command.stepAmount(),
                command.percentageRate(),
                command.ceilingThreshold(),
                true
        );

        savingsRuleDao.insert(rule);
        savingsPlanDao.touchUpdatedAt(planId);

        return toRuleDto(rule);
    }

    @Override
    @Transactional
    public void removeRule(@NonNull final UUID ruleId) {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        SavingsRule rule = savingsRuleDao.findById(ruleId)
                .orElseThrow(() -> new NoSuchElementException("Savings rule not found: " + ruleId));
        savingsRuleDao.deleteById(ruleId);
        savingsPlanDao.touchUpdatedAt(rule.planId());
    }

    @Override
    @Transactional
    public void toggleRule(@NonNull final UUID ruleId, final boolean isActive) {
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        SavingsRule rule = savingsRuleDao.findById(ruleId)
                .orElseThrow(() -> new NoSuchElementException("Savings rule not found: " + ruleId));
        savingsRuleDao.updateActive(ruleId, isActive);
        savingsPlanDao.touchUpdatedAt(rule.planId());
    }

    @Override
    public List<SavingsRuleDto> getRulesForPlan(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        if (savingsPlanDao.findById(planId).isEmpty()) {
            throw new NoSuchElementException("Savings plan not found: " + planId);
        }
        return savingsRuleDao.findByPlanId(planId).stream()
                .map(this::toRuleDto)
                .toList();
    }

    private void validateRuleCommand(CreateSavingsRuleCommand command) {
        if (command.ruleType() == null) {
            throw new IllegalArgumentException("Rule type cannot be null");
        }
        switch (command.ruleType()) {
            case ROUND_UP -> {
                if (command.stepAmount() == null || command.stepAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Round-up step amount must be greater than zero");
                }
            }
            case PERCENTAGE -> {
                if (command.percentageRate() == null
                        || command.percentageRate().compareTo(BigDecimal.ZERO) <= 0
                        || command.percentageRate().compareTo(new BigDecimal("100.00")) > 0) {
                    throw new IllegalArgumentException("Percentage rate must be between 0 and 100");
                }
            }
            case THRESHOLD -> {
                if (command.ceilingThreshold() == null || command.ceilingThreshold().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Threshold ceiling must be greater than zero");
                }
            }
        }
    }

    private SavingsRuleDto toRuleDto(SavingsRule r) {
        return new SavingsRuleDto(
                r.id(),
                r.planId(),
                r.ruleType(),
                r.stepAmount(),
                r.percentageRate(),
                r.ceilingThreshold(),
                r.isActive()
        );
    }

    private SavingsPlanDto toDto(SavingsPlan plan) {
        List<SavingsRuleDto> ruleDtos = plan.rules().stream()
                .map(this::toRuleDto)
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

