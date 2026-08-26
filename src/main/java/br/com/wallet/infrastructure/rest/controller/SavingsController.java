package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.infrastructure.rest.api.SavingsApi;
import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.SavingsQueryUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/savings")
public class SavingsController implements SavingsApi {

    private static final Logger log = LoggerFactory.getLogger(SavingsController.class);

    private final SavingsPlanUseCase savingsPlanUseCase;
    private final SavingsQueryUseCase savingsQueryUseCase;

    public SavingsController(
            @NonNull final SavingsPlanUseCase savingsPlanUseCase,
            @NonNull final SavingsQueryUseCase savingsQueryUseCase
    ) {
        this.savingsPlanUseCase = Objects.requireNonNull(savingsPlanUseCase, "savingsPlanUseCase cannot be null");
        this.savingsQueryUseCase = Objects.requireNonNull(savingsQueryUseCase, "savingsQueryUseCase cannot be null");
    }

    @PostMapping("/plans")
    @Override
    public ResponseEntity<SavingsPlanDto> createPlan(@Valid @RequestBody final CreateSavingsPlanCommand command) {
        log.info("Create savings plan requested. source={}, target={}", command.sourceWalletId(), command.targetWalletId());
        SavingsPlanDto created = savingsPlanUseCase.createPlan(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/plans/{planId}")
    @Override
    public ResponseEntity<SavingsPlanDto> getPlan(@PathVariable final UUID planId) {
        return ResponseEntity.ok(savingsPlanUseCase.getPlan(planId));
    }

    @GetMapping("/plans")
    @Override
    public ResponseEntity<List<SavingsPlanDto>> listPlans(
            @RequestParam(defaultValue = "100") final Integer limit,
            @RequestParam(defaultValue = "0") final Integer offset
    ) {
        log.debug("List all savings plans requested. limit={}, offset={}", limit, offset);
        return ResponseEntity.ok(savingsPlanUseCase.listPlans(limit, offset));
    }

    @GetMapping("/plans/source/{sourceWalletId}")
    @Override
    public ResponseEntity<List<SavingsPlanDto>> getPlansBySourceWallet(@PathVariable final UUID sourceWalletId) {
        log.debug("Get savings plans by source wallet requested. sourceWalletId={}", sourceWalletId);
        return ResponseEntity.ok(savingsPlanUseCase.getPlansBySourceWallet(sourceWalletId));
    }

    @GetMapping("/plans/target/{targetWalletId}")
    @Override
    public ResponseEntity<List<SavingsPlanDto>> getPlansByTargetWallet(@PathVariable final UUID targetWalletId) {
        log.debug("Get savings plans by target wallet requested. targetWalletId={}", targetWalletId);
        return ResponseEntity.ok(savingsPlanUseCase.getPlansByTargetWallet(targetWalletId));
    }

    @GetMapping("/plans/wallet/{walletId}")
    @Override
    public ResponseEntity<List<SavingsPlanDto>> getPlansForWallet(@PathVariable final UUID walletId) {
        log.debug("Get all savings plans for wallet requested. walletId={}", walletId);
        return ResponseEntity.ok(savingsPlanUseCase.getPlansForWallet(walletId));
    }

    @PostMapping("/plans/{planId}/pause")
    @Override
    public ResponseEntity<Void> pausePlan(@PathVariable final UUID planId) {
        log.info("Pause savings plan requested. planId={}", planId);
        savingsPlanUseCase.pausePlan(planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plans/{planId}/resume")
    @Override
    public ResponseEntity<Void> resumePlan(@PathVariable final UUID planId) {
        log.info("Resume savings plan requested. planId={}", planId);
        savingsPlanUseCase.resumePlan(planId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/plans/{planId}")
    @Override
    public ResponseEntity<Void> deletePlan(@PathVariable final UUID planId) {
        log.info("Delete savings plan requested. planId={}", planId);
        savingsPlanUseCase.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plans/{planId}/rules")
    @Override
    public ResponseEntity<SavingsRuleDto> addRule(
            @PathVariable final UUID planId,
            @Valid @RequestBody final CreateSavingsRuleCommand command
    ) {
        log.info("Add savings rule requested. planId={}, ruleType={}", planId, command.ruleType());
        SavingsRuleDto created = savingsPlanUseCase.addRule(planId, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/plans/{planId}/rules")
    @Override
    public ResponseEntity<List<SavingsRuleDto>> getRulesForPlan(@PathVariable final UUID planId) {
        return ResponseEntity.ok(savingsPlanUseCase.getRulesForPlan(planId));
    }

    @PatchMapping("/rules/{ruleId}/status")
    @Override
    public ResponseEntity<Void> toggleRuleStatus(
            @PathVariable final UUID ruleId,
            @RequestParam final boolean active
    ) {
        log.info("Toggle savings rule status requested. ruleId={}, active={}", ruleId, active);
        savingsPlanUseCase.toggleRule(ruleId, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rules/{ruleId}")
    @Override
    public ResponseEntity<Void> deleteRule(@PathVariable final UUID ruleId) {
        log.info("Delete savings rule requested. ruleId={}", ruleId);
        savingsPlanUseCase.removeRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/metrics/{walletId}")
    @Override
    public ResponseEntity<SavingsMetricsResponse> getMetrics(@PathVariable final UUID walletId) {
        return ResponseEntity.ok(savingsQueryUseCase.getMetrics(walletId));
    }
}
