package br.com.wallet.interfaces.rest.api;

import br.com.wallet.interfaces.rest.dto.DepositCommand;
import br.com.wallet.interfaces.rest.dto.TransferCommand;
import br.com.wallet.interfaces.rest.dto.WithdrawCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.UUID;

/**
 * This is used for basic open api documentation
 */
public interface OperationsApi {

    @Operation(summary = "Transfer funds between wallets")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds")
    })
    void transfer(UUID operationId, TransferCommand request);


    @Operation(summary = "Deposit funds into a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Invalid amount!")
    })
    void deposit(UUID operationId, DepositCommand request);

    @Operation(summary = "Withdraw funds from a wallet")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Success"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "422", description = "Insufficient funds")
    })
    void withdraw(UUID operationId, WithdrawCommand request);
}
