package br.com.wallet.application.service;

import br.com.wallet.application.aspects.tracing.Traceable;
import br.com.wallet.infrasctructure.persistence.OutboxDao;
import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import br.com.wallet.application.core.WalletOperationService;
import br.com.wallet.application.usecase.DepositFundsUseCase;
import br.com.wallet.application.utils.Validations;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.event.DepositCompletedEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
class DepositFundsService implements DepositFundsUseCase {

    private static final Logger log = LoggerFactory.getLogger(DepositFundsService.class);

    private final WalletOperationService core;
    private final WalletOperationsDao operationsDao;
    private final OutboxDao outboxDao;

    public DepositFundsService(final WalletOperationService core,
                               final WalletOperationsDao operationsDao, OutboxDao outboxDao) {
        this.core = core;
        this.operationsDao = operationsDao;
        this.outboxDao = outboxDao;
    }

    @Traceable("wallet.deposit")
    @Transactional
    public void execute(@NonNull UUID walletId, @NonNull BigDecimal amount, @NonNull UUID operationId) {
        // validations
        Validations.validatePositiveAmount(amount);

        if (operationsDao.registerOperation(operationId)) {
            log.info("Idempotent operation ignored. operationId={}", operationId);
            return; // idempotent: already processed!
        }

        final var now = Instant.now();
        core.applyTransaction(walletId, amount, LedgerType.CREDIT, operationId, now);
        outboxDao.save(
                new DepositCompletedEvent(walletId, amount, operationId)
        );
    }


}
