package br.com.wallet.interfaces.rest.api;

import br.com.wallet.interfaces.rest.dto.BalanceResponse;
import br.com.wallet.interfaces.rest.dto.CreateWalletCommand;
import br.com.wallet.interfaces.rest.dto.CreateWalletResponse;
import br.com.wallet.interfaces.rest.dto.LedgerEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WalletApi {

    @Operation(summary = "Get wallet balance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    BalanceResponse getBalance(UUID walletId);

    @Operation(summary = "Get wallet balance at a point in time")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "422", description = "Invalid date")
    })
    BalanceResponse getHistorical(
            UUID walletId,
            Instant at
    );

    @Operation(summary = "Create wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    ResponseEntity<CreateWalletResponse> createWallet(
            UUID operationId,
            @Valid CreateWalletCommand command
    );

    @Operation(summary = "Get wallet ledger")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "422", description = "Limit too high! Choose a smaller limit (< 1000) ")
    })
    ResponseEntity<List<LedgerEntryResponse>> getLedger(
            UUID walletId, @Max(value = 1000, message = "Limit too high! Choose a smaller limit (< 1000) ") Integer limit
    );

    @Operation(summary = "Replay wallet balance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    BalanceResponse replay(UUID walletId);
}
