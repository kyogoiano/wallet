package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.infrastructure.messaging.publisher.NatsCommandPublisher;
import br.com.wallet.infrastructure.rest.api.OperationsApi;
import br.com.wallet.infrastructure.rest.dto.DepositCommand;
import br.com.wallet.infrastructure.rest.dto.TransferCommand;
import br.com.wallet.infrastructure.rest.dto.WithdrawCommand;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/operations")
public class OperationsController implements OperationsApi {

    private static final Logger log = LoggerFactory.getLogger(OperationsController.class);
    private final NatsCommandPublisher natsCommandPublisher;
    private final FraudCheckHelper fraudCheckHelper;

    public OperationsController(final NatsCommandPublisher natsCommandPublisher,
                                final FraudCheckHelper fraudCheckHelper) {
        this.natsCommandPublisher = natsCommandPublisher;
        this.fraudCheckHelper = fraudCheckHelper;
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<Void> transfer(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final TransferCommand command) {
        
        log.info("Transfer requested: from={}, to={}, amount={}",
                command.from(), command.to(), command.amount());
        
        var transfer = new Transfer(command.from(), command.to(), command.amount(), operationId);

        fraudCheckHelper.performFraudCheck(transfer);

        return natsCommandPublisher.publishAsync("commands.transfer", transfer)
                .thenAccept(ack -> log.debug("Transfer command ACKed by NATS: seq={}", ack.getSeqno()));
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<Void> deposit(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final DepositCommand command) {

        var deposit = new Deposit(command.walletId(), command.userId(), command.amount(), operationId);

        fraudCheckHelper.performFraudCheck(deposit);

        return natsCommandPublisher.publishAsync("commands.deposit", deposit)
                .thenAccept(ack -> log.debug("Deposit command ACKed by NATS: seq={}", ack.getSeqno()));
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<Void> withdraw(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final WithdrawCommand command) {

        final var withdraw = new Withdraw(command.walletId(), command.userId(), command.amount(), operationId);

        fraudCheckHelper.performFraudCheck(withdraw);

        return natsCommandPublisher.publishAsync("commands.withdraw", withdraw)
                .thenAccept(ack -> log.debug("Withdraw command ACKed by NATS: seq={}", ack.getSeqno()));
    }
}
