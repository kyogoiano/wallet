package br.com.wallet.ledger.internal.listener;

import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.ledger.api.exceptions.ExceptionType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Spring Modulith in-process listener for executing Withdraw bounded context commands.
 * Replaces legacy NATS WithdrawCommandConsumer without broker roundtrips (REQ-STRM-009, I-STREAM-009).
 */
@Component
public class WithdrawCommandListener {

    private static final Logger log = LoggerFactory.getLogger(WithdrawCommandListener.class);
    private final WithdrawFundsUseCase useCase;
    private final OperationStateUseCase operationStateUseCase;

    public WithdrawCommandListener(
            @NonNull final WithdrawFundsUseCase useCase,
            @NonNull final OperationStateUseCase operationStateUseCase
    ) {
        this.useCase = Objects.requireNonNull(useCase, "useCase cannot be null");
        this.operationStateUseCase = Objects.requireNonNull(operationStateUseCase, "operationStateUseCase cannot be null");
    }

    @ApplicationModuleListener
    public void onWithdrawCommand(@NonNull final Withdraw command) {
        Objects.requireNonNull(command, "command cannot be null");
        log.info("Processing Withdraw command in Modulith listener: opId={}", command.operationId());
        try {
            useCase.handle(command);
        } catch (IdempotencyException ie) {
            log.info("Idempotent command replay detected for opId={}: {}", command.operationId(), ie.getMessage());
        } catch (Exception e) {
            log.error("Failed to execute withdraw command: opId={}, error={}", command.operationId(), e.getMessage());
            String tenant = command.tenantId();
            try {
                operationStateUseCase.markOperationFailed(
                        command.operationId(),
                        e.getMessage(),
                        ExceptionType.parseException(e).name(),
                        tenant
                );
            } catch (Exception ex) {
                log.warn("Could not mark operation failed in listener: opId={}, error={}", command.operationId(), ex.getMessage());
            }
            throw e;
        }
    }
}
