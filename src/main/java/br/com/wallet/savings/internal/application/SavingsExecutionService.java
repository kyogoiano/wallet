package br.com.wallet.savings.internal.application;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.savings.api.model.SavingsExecutionStatus;
import br.com.wallet.savings.internal.domain.IntendedSweepAction;
import br.com.wallet.savings.internal.persistence.SavingsExecutionHistoryDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

@Service
public class SavingsExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SavingsExecutionService.class);

    private final TransferFundsUseCase transferFundsUseCase;
    private final SavingsExecutionHistoryDao historyDao;

    public SavingsExecutionService(
            @NonNull final TransferFundsUseCase transferFundsUseCase,
            @NonNull final SavingsExecutionHistoryDao historyDao
    ) {
        this.transferFundsUseCase = Objects.requireNonNull(transferFundsUseCase, "transferFundsUseCase cannot be null");
        this.historyDao = Objects.requireNonNull(historyDao, "historyDao cannot be null");
    }

    @NonNull
    public UUID generateSavingsOperationId(@NonNull final UUID sourceOperationId, @NonNull final UUID ruleId) {
        Objects.requireNonNull(sourceOperationId, "sourceOperationId cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");

        String seed = sourceOperationId + ":" + ruleId + ":SAVINGS_SWEEP";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return UUID.nameUUIDFromBytes(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public void executeSweep(
            @NonNull final IntendedSweepAction action,
            @NonNull final UUID sourceOperationId,
            @NonNull final String triggerEventType
    ) {
        log.info("Executing sweep operation {}", action);
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(sourceOperationId, "sourceOperationId cannot be null");
        Objects.requireNonNull(triggerEventType, "triggerEventType cannot be null");

        UUID savingsOperationId = generateSavingsOperationId(sourceOperationId, action.ruleId());

        if (historyDao.isOperationProcessed(savingsOperationId)) {
            log.info("Savings sweep already processed. savingsOperationId={}, ruleId={}", savingsOperationId, action.ruleId());
            return;
        }

        Transfer transfer = new Transfer(
                action.sourceWalletId(),
                action.targetWalletId(),
                action.sweepAmount(),
                savingsOperationId,
                OperationOrigin.SAVINGS_AUTOMATION
        );

        try {
            transferFundsUseCase.handle(transfer);
            historyDao.insertExecution(
                    savingsOperationId, action.planId(), action.ruleId(), sourceOperationId,
                    triggerEventType, action.sweepAmount(), action.sweepAmount(),
                    SavingsExecutionStatus.EXECUTED, null
            );
            log.info("Savings sweep executed successfully. savingsOpId={}, amount={}, ruleType={}",
                    savingsOperationId, action.sweepAmount(), action.ruleType());
        } catch (InsufficientFundsException e) {
            log.warn("Savings sweep skipped due to insufficient funds. savingsOpId={}, planId={}", savingsOperationId, action.planId());
            historyDao.insertExecution(
                    savingsOperationId, action.planId(), action.ruleId(), sourceOperationId,
                    triggerEventType, action.sweepAmount(), BigDecimal.ZERO,
                    SavingsExecutionStatus.SKIPPED_INSUFFICIENT_FUNDS, e.getMessage()
            );
        } catch (AccountBlockedException | FraudBlockedException e) {
            log.warn("Savings sweep rejected by fraud engine. savingsOpId={}, planId={}", savingsOperationId, action.planId());
            historyDao.insertExecution(
                    savingsOperationId, action.planId(), action.ruleId(), sourceOperationId,
                    triggerEventType, action.sweepAmount(), BigDecimal.ZERO,
                    SavingsExecutionStatus.REJECTED_BY_FRAUD, e.getMessage()
            );
        } catch (Exception e) {
            log.error("Savings sweep failed with exception. savingsOpId={}, planId={}", savingsOperationId, action.planId(), e);
            historyDao.insertExecution(
                    savingsOperationId, action.planId(), action.ruleId(), sourceOperationId,
                    triggerEventType, action.sweepAmount(), BigDecimal.ZERO,
                    SavingsExecutionStatus.FAILED_PERMANENT, e.getMessage()
            );
        }
    }
}
