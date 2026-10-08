package br.com.wallet.interfaces.rest.api;

import br.com.wallet.interfaces.rest.dto.DepositCommand;
import br.com.wallet.interfaces.rest.dto.TransferCommand;
import br.com.wallet.interfaces.rest.dto.WithdrawCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * This is used for basic open api documentation
 */
public interface OperationsApi {

    @Operation(summary = "Transfer funds between wallets")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds")
    })
    CompletableFuture<Void> transfer(UUID operationId, TransferCommand request);


    @Operation(summary = "Deposit funds into a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount!")
    })
    CompletableFuture<Void> deposit(UUID operationId, DepositCommand request);

    @Operation(summary = "Withdraw funds from a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds")
    })
    CompletableFuture<Void> withdraw(UUID operationId, WithdrawCommand request);
}
