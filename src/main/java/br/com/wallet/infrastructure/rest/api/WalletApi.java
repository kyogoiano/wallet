package br.com.wallet.infrastructure.rest.api;

import br.com.wallet.infrastructure.rest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WalletApi {

    @Operation(summary = "Get wallet account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    AccountResponse getAccount(UUID walletId);

    @Operation(summary = "List paginated wallets account")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "422", description = "Limit too high!")
    })
    List<AccountResponse> list(@Max(100) Integer limit, Integer offset);

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
            @NotNull Instant at
    );

    @Operation(summary = "Create a simple empty wallet (Synchronous)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    ResponseEntity<CreateWalletResponse> create(@NotNull UUID userId);

    @Operation(summary = "Create wallet with initial deposit (Asynchronous)")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Validation error")
    })
    CompletableFuture<ResponseEntity<CreateWalletResponse>> createWithDeposit(
            UUID operationId,

            @Valid CreateWalletCommand command
    );

    @Operation(summary = "Get wallet ledger")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found"),
            @ApiResponse(responseCode = "422", description = "Limit too high!")
    })
    ResponseEntity<List<LedgerEntryResponse>> getLedger(
            UUID walletId, @Max(value = 1000) Integer limit
    );

    @Operation(summary = "Replay wallet balance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    BalanceResponse replay(UUID walletId);
}
