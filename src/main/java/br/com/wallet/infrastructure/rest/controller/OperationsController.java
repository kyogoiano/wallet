package br.com.wallet.infrastructure.rest.controller;

import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.infrastructure.rest.api.OperationsApi;
import br.com.wallet.infrastructure.rest.dto.DepositCommand;
import br.com.wallet.infrastructure.rest.dto.TransferCommand;
import br.com.wallet.infrastructure.rest.dto.WithdrawCommand;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/operations")
public class OperationsController implements OperationsApi {

    private static final Logger log = LoggerFactory.getLogger(OperationsController.class);
    private final ApplicationEventPublisher eventPublisher;
    private final FraudCheckHelper fraudCheckHelper;
    private final OperationQueryUseCase operationQueryUseCase;

    public OperationsController(final ApplicationEventPublisher eventPublisher,
                                final FraudCheckHelper fraudCheckHelper,
                                final OperationQueryUseCase operationQueryUseCase) {
        this.eventPublisher = eventPublisher;
        this.fraudCheckHelper = fraudCheckHelper;
        this.operationQueryUseCase = operationQueryUseCase;
    }

    @GetMapping("/{operationId:[0-9a-fA-F\\-]+}")
    @Override
    public OperationStatusResponse getOperationStatus(@PathVariable final UUID operationId) {
        return operationQueryUseCase.getOperationStatus(operationId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Operation not found: " + operationId));
    }

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<Void> transfer(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final TransferCommand command) {
        
        log.info("Transfer requested: from={}, to={}, amount={}",
                command.from(), command.to(), command.amount());
        
        String tenantId = command.tenantId() != null && !command.tenantId().isBlank()
                ? command.tenantId()
                : "tenant-alpha";
        var transfer = new Transfer(command.from(), command.to(), command.amount(), operationId, tenantId);

        fraudCheckHelper.performFraudCheck(transfer);

        eventPublisher.publishEvent(transfer);
        return CompletableFuture.completedFuture(null);
    }

    @PostMapping("/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<Void> deposit(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final DepositCommand command) {

        String tenantId = command.tenantId() != null && !command.tenantId().isBlank()
                ? command.tenantId()
                : "tenant-alpha";
        var deposit = new Deposit(command.walletId(), command.userId(), command.amount(), operationId, tenantId);

        fraudCheckHelper.performFraudCheck(deposit);

        eventPublisher.publishEvent(deposit);
        return CompletableFuture.completedFuture(null);
    }

    @PostMapping("/withdraw")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public CompletableFuture<Void> withdraw(
            @RequestHeader("Idempotency-Key") UUID operationId,
            @RequestBody @Valid final WithdrawCommand command) {

        String tenantId = command.tenantId() != null && !command.tenantId().isBlank()
                ? command.tenantId()
                : "tenant-alpha";
        final var withdraw = new Withdraw(command.walletId(), command.userId(), command.amount(), operationId, tenantId);

        fraudCheckHelper.performFraudCheck(withdraw);

        eventPublisher.publishEvent(withdraw);
        return CompletableFuture.completedFuture(null);
    }
}
