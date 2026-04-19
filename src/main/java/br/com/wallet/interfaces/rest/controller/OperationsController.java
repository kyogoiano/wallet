package br.com.wallet.interfaces.rest.controller;

import br.com.wallet.domain.context.Deposit;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Withdraw;
import br.com.wallet.infrasctructure.messaging.publisher.NatsCommandPublisher;
import br.com.wallet.interfaces.rest.api.OperationsApi;
import br.com.wallet.interfaces.rest.dto.DepositCommand;
import br.com.wallet.interfaces.rest.dto.TransferCommand;
import br.com.wallet.interfaces.rest.dto.WithdrawCommand;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/operations")
public class OperationsController implements OperationsApi {


    private static final Logger log = LoggerFactory.getLogger(OperationsController.class);
    private final NatsCommandPublisher natsCommandPublisher;

    public OperationsController(
            final NatsCommandPublisher natsCommandPublisher
    ) {
        this.natsCommandPublisher = natsCommandPublisher;
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
        log.info("Transfer requested: from={}, to={}, amount={}",
                command.from(), command.to(), command.amount());
        var transfer = new Transfer(command.from(), command.to(), command.amount(), operationId);
        natsCommandPublisher.publish("commands.transfer", transfer);
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void deposit(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final DepositCommand command) {
        var deposit = new Deposit(command.walletId(), command.amount(), operationId);
        natsCommandPublisher.publish("commands.deposit", deposit);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void withdraw(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final WithdrawCommand command) {
        var withdraw = new Withdraw(command.walletId(), command.amount(), operationId);
        natsCommandPublisher.publish("commands.withdraw", withdraw);
    }
}
