package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.goals.api.CashflowProfileUseCase;
import br.com.wallet.goals.api.FinancialGoalUseCase;
import br.com.wallet.goals.api.GoalQueryUseCase;
import br.com.wallet.goals.api.GoalStrategyUseCase;
import br.com.wallet.goals.api.dto.*;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.goals.api.model.MultiGoalStrategyReport;
import br.com.wallet.infrastructure.rest.api.GoalApi;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/goals")
public class GoalController implements GoalApi {

    private static final Logger log = LoggerFactory.getLogger(GoalController.class);

    private final FinancialGoalUseCase goalUseCase;
    private final GoalQueryUseCase queryUseCase;
    private final CashflowProfileUseCase cashflowProfileUseCase;
    private final GoalStrategyUseCase strategyUseCase;

    public GoalController(
            @NonNull final FinancialGoalUseCase goalUseCase,
            @NonNull final GoalQueryUseCase queryUseCase,
            @NonNull final CashflowProfileUseCase cashflowProfileUseCase,
            @NonNull final GoalStrategyUseCase strategyUseCase
    ) {
        this.goalUseCase = Objects.requireNonNull(goalUseCase, "goalUseCase cannot be null");
        this.queryUseCase = Objects.requireNonNull(queryUseCase, "queryUseCase cannot be null");
        this.cashflowProfileUseCase = Objects.requireNonNull(cashflowProfileUseCase, "cashflowProfileUseCase cannot be null");
        this.strategyUseCase = Objects.requireNonNull(strategyUseCase, "strategyUseCase cannot be null");
    }

    @PostMapping
    @Override
    public ResponseEntity<GoalResponse> createGoal(@Valid @RequestBody final CreateGoalCommand command) {
        log.info("Create goal requested: wallet={}, name={}", command.walletId(), command.name());
        final GoalResponse created = goalUseCase.createGoal(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{goalId}")
    @Override
    public ResponseEntity<GoalResponse> getGoal(@PathVariable final UUID goalId) {
        return queryUseCase.findGoalById(goalId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/wallet/{walletId}")
    @Override
    public ResponseEntity<List<GoalResponse>> getGoalsByWallet(@PathVariable final UUID walletId) {
        return ResponseEntity.ok(queryUseCase.findGoalsByWalletId(walletId));
    }

    @PutMapping("/{goalId}")
    @Override
    public ResponseEntity<GoalResponse> updateGoal(@PathVariable final UUID goalId, @Valid @RequestBody final UpdateGoalCommand command) {
        log.info("Update goal requested: id={}", goalId);
        final GoalResponse updated = goalUseCase.updateGoal(goalId, command);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{goalId}/pause")
    @Override
    public ResponseEntity<Void> pauseGoal(@PathVariable final UUID goalId) {
        goalUseCase.pauseGoal(goalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{goalId}/resume")
    @Override
    public ResponseEntity<Void> resumeGoal(@PathVariable final UUID goalId) {
        goalUseCase.resumeGoal(goalId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{goalId}")
    @Override
    public ResponseEntity<Void> cancelGoal(@PathVariable final UUID goalId) {
        goalUseCase.cancelGoal(goalId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/cashflow")
    @Override
    public ResponseEntity<CashflowProfile> saveCashflowProfile(@Valid @RequestBody final SaveCashflowProfileCommand command) {
        log.info("Save cashflow profile requested: wallet={}", command.walletId());
        final CashflowProfile saved = cashflowProfileUseCase.saveCashflowProfile(command);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/cashflow/wallet/{walletId}")
    @Override
    public ResponseEntity<CashflowProfile> getCashflowProfile(@PathVariable final UUID walletId) {
        return cashflowProfileUseCase.getCashflowProfileByWalletId(walletId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{goalId}/strategy")
    @Override
    public ResponseEntity<GoalStrategyResponse> calculateStrategy(
            @PathVariable final UUID goalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate evaluationDate
    ) {
        final LocalDate date = evaluationDate != null ? evaluationDate : LocalDate.now();
        return ResponseEntity.ok(strategyUseCase.calculateStrategy(goalId, date));
    }

    @PostMapping("/simulate")
    @Override
    public ResponseEntity<GoalStrategyResponse> simulateStrategy(
            @Valid @RequestBody final SimulateGoalCommand command,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate evaluationDate
    ) {
        final LocalDate date = evaluationDate != null ? evaluationDate : LocalDate.now();
        return ResponseEntity.ok(strategyUseCase.simulate(command, date));
    }

    @GetMapping("/wallet/{walletId}/strategy-report")
    @Override
    public ResponseEntity<MultiGoalStrategyReport> getWalletStrategyReport(
            @PathVariable final UUID walletId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate evaluationDate
    ) {
        final LocalDate date = evaluationDate != null ? evaluationDate : LocalDate.now();
        return ResponseEntity.ok(strategyUseCase.evaluateWallet(walletId, date));
    }
}
