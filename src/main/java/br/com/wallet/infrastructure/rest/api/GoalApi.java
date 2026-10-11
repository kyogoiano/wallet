package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.goals.api.dto.*;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.goals.api.model.MultiGoalStrategyReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Financial Goals & Strategy API", description = "Endpoints for goal lifecycle, cashflow planning, and deterministic strategy simulation")
public interface GoalApi {

    @Operation(summary = "Create a financial goal")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Goal created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid goal parameters")
    })
    ResponseEntity<GoalResponse> createGoal(@Valid CreateGoalCommand command);

    @Operation(summary = "Get a financial goal by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal found"),
            @ApiResponse(responseCode = "404", description = "Goal not found")
    })
    ResponseEntity<GoalResponse> getGoal(UUID goalId);

    @Operation(summary = "List all financial goals for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of goals returned")
    })
    ResponseEntity<List<GoalResponse>> getGoalsByWallet(UUID walletId);

    @Operation(summary = "Update a financial goal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal updated successfully"),
            @ApiResponse(responseCode = "404", description = "Goal not found")
    })
    ResponseEntity<GoalResponse> updateGoal(UUID goalId, @Valid UpdateGoalCommand command);

    @Operation(summary = "Pause an active financial goal")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Goal paused"),
            @ApiResponse(responseCode = "404", description = "Goal not found")
    })
    ResponseEntity<Void> pauseGoal(UUID goalId);

    @Operation(summary = "Resume a paused financial goal")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Goal resumed"),
            @ApiResponse(responseCode = "404", description = "Goal not found")
    })
    ResponseEntity<Void> resumeGoal(UUID goalId);

    @Operation(summary = "Cancel a financial goal")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Goal cancelled"),
            @ApiResponse(responseCode = "404", description = "Goal not found")
    })
    ResponseEntity<Void> cancelGoal(UUID goalId);

    @Operation(summary = "Save or update cashflow profile for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cashflow profile saved successfully")
    })
    ResponseEntity<CashflowProfile> saveCashflowProfile(@Valid SaveCashflowProfileCommand command);

    @Operation(summary = "Get cashflow profile for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cashflow profile found"),
            @ApiResponse(responseCode = "404", description = "Cashflow profile not found")
    })
    ResponseEntity<CashflowProfile> getCashflowProfile(UUID walletId);

    @Operation(summary = "Calculate dynamic strategy and feasibility for a goal")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Strategy calculated successfully"),
            @ApiResponse(responseCode = "404", description = "Goal not found")
    })
    ResponseEntity<GoalStrategyResponse> calculateStrategy(UUID goalId, LocalDate evaluationDate);

    @Operation(summary = "Simulate a goal strategy statelessly without database writes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulation executed successfully")
    })
    ResponseEntity<GoalStrategyResponse> simulateStrategy(@Valid SimulateGoalCommand command, LocalDate evaluationDate);

    @Operation(summary = "Get multi-goal waterfall strategy report for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Strategy report generated successfully")
    })
    ResponseEntity<MultiGoalStrategyReport> getWalletStrategyReport(UUID walletId, LocalDate evaluationDate);
}
