package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.savings.api.dto.CreateSavingsPlanCommand;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.dto.SavingsMetricsResponse;
import br.com.wallet.savings.api.model.SavingsPlanDto;
import br.com.wallet.savings.api.model.SavingsRuleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

public interface SavingsApi {

    @Operation(summary = "Create a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<SavingsPlanDto> createPlan(@Valid @NotNull CreateSavingsPlanCommand command);

    @Operation(summary = "Get savings plan by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<SavingsPlanDto> getPlan(@NotNull UUID planId);

    @Operation(summary = "Get all savings plans for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<List<SavingsPlanDto>> getPlansForWallet(@NotNull UUID walletId);

    @Operation(summary = "Pause a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<Void> pausePlan(@NotNull UUID planId);

    @Operation(summary = "Resume a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<Void> resumePlan(@NotNull UUID planId);

    @Operation(summary = "Delete a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<Void> deletePlan(@NotNull UUID planId);

    @Operation(summary = "Add a savings rule to an existing savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<SavingsRuleDto> addRule(@NotNull UUID planId, @Valid @NotNull CreateSavingsRuleCommand command);

    @Operation(summary = "Get all savings rules for a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<List<SavingsRuleDto>> getRulesForPlan(@NotNull UUID planId);

    @Operation(summary = "Toggle active status of a savings rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings rule not found")
    })
    ResponseEntity<Void> toggleRuleStatus(@NotNull UUID ruleId, boolean active);

    @Operation(summary = "Delete a savings rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings rule not found")
    })
    ResponseEntity<Void> deleteRule(@NotNull UUID ruleId);

    @Operation(summary = "Get savings metrics for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<SavingsMetricsResponse> getMetrics(@NotNull UUID walletId);
}
