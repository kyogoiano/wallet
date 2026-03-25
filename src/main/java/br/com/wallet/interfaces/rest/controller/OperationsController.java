package br.com.wallet.interfaces.rest.controller;

import br.com.wallet.application.usecase.DepositFundsUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.application.usecase.WithdrawFundsUseCase;
import br.com.wallet.interfaces.rest.api.OperationsApi;
import br.com.wallet.interfaces.rest.dto.DepositCommand;
import br.com.wallet.interfaces.rest.dto.TransferCommand;
import br.com.wallet.interfaces.rest.dto.WithdrawCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/operations")
public class OperationsController implements OperationsApi {

    private final TransferFundsUseCase transfer;
    private final DepositFundsUseCase deposit;
    private final WithdrawFundsUseCase withdraw;

    public OperationsController(
            final TransferFundsUseCase transfer,
            final DepositFundsUseCase deposit,
            final WithdrawFundsUseCase withdraw
    ) {
        this.transfer = transfer;
        this.deposit = deposit;
        this.withdraw = withdraw;
    }

    /**
     * Stripe style transfer request
     *
     * @param operationId Idempotency key
     * @param command     Transfer business command
     */
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void transfer(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final TransferCommand command) {
        transfer.execute(
                command.from(),
                command.to(),
                command.amount(),
                operationId
        );
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void deposit(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final DepositCommand command) {
        deposit.execute(
                command.walletId(),
                command.amount(),
                operationId
        );
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void withdraw(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final WithdrawCommand command) {
        withdraw.execute(
                command.walletId(),
                command.amount(),
                operationId
        );
    }
}
