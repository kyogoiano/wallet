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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

public interface SavingsApi {

    @Operation(summary = "Create a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<SavingsPlanDto> createPlan(@Valid CreateSavingsPlanCommand command);

    @Operation(summary = "Get savings plan by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<SavingsPlanDto> getPlan(UUID planId);

    @Operation(summary = "List all savings plans with pagination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<List<SavingsPlanDto>> listPlans(Integer limit, Integer offset);

    @Operation(summary = "Get savings plans by source wallet ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<List<SavingsPlanDto>> getPlansBySourceWallet(UUID sourceWalletId);

    @Operation(summary = "Get savings plans by target wallet ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<List<SavingsPlanDto>> getPlansByTargetWallet(UUID targetWalletId);

    @Operation(summary = "Get all savings plans associated with a wallet (source or target)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<List<SavingsPlanDto>> getPlansForWallet(UUID walletId);

    @Operation(summary = "Pause a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<Void> pausePlan(UUID planId);

    @Operation(summary = "Resume a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<Void> resumePlan(UUID planId);

    @Operation(summary = "Delete a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<Void> deletePlan(UUID planId);

    @Operation(summary = "Add a savings rule to an existing savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<SavingsRuleDto> addRule(UUID planId, @Valid CreateSavingsRuleCommand command);

    @Operation(summary = "Get all savings rules for a savings plan")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "404", description = "Savings plan not found")
    })
    ResponseEntity<List<SavingsRuleDto>> getRulesForPlan(UUID planId);

    @Operation(summary = "Toggle active status of a savings rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings rule not found")
    })
    ResponseEntity<Void> toggleRuleStatus(UUID ruleId, boolean active);

    @Operation(summary = "Delete a savings rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "No Content"),
            @ApiResponse(responseCode = "404", description = "Savings rule not found")
    })
    ResponseEntity<Void> deleteRule(UUID ruleId);

    @Operation(summary = "Get savings metrics for a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<SavingsMetricsResponse> getMetrics(UUID walletId);
}
