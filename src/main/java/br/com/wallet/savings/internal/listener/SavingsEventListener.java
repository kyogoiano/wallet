package br.com.wallet.savings.internal.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.event.DepositCompletedEvent;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.savings.internal.application.SavingsExecutionService;
import br.com.wallet.savings.internal.domain.IntendedSweepAction;
import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.engine.SavingsRuleEngine;
import br.com.wallet.savings.internal.persistence.SavingsPlanDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Component
public class SavingsEventListener {

    private static final Logger log = LoggerFactory.getLogger(SavingsEventListener.class);

    private final SavingsPlanDao savingsPlanDao;
    private final SavingsRuleEngine ruleEngine;
    private final SavingsExecutionService executionService;
    private final BalanceUseCase balanceUseCase;

    public SavingsEventListener(
            @NonNull final SavingsPlanDao savingsPlanDao,
            @NonNull final SavingsRuleEngine ruleEngine,
            @NonNull final SavingsExecutionService executionService,
            @NonNull final BalanceUseCase balanceUseCase
    ) {
        this.savingsPlanDao = Objects.requireNonNull(savingsPlanDao, "savingsPlanDao cannot be null");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine cannot be null");
        this.executionService = Objects.requireNonNull(executionService, "executionService cannot be null");
        this.balanceUseCase = Objects.requireNonNull(balanceUseCase, "balanceUseCase cannot be null");
    }

    @ApplicationModuleListener
    public void onDeposit(@NonNull final DepositCompletedEvent event) {
        log.info("onDeposit({})", event);
        Objects.requireNonNull(event, "event cannot be null");

        // O(1) Loop Prevention
        if (event.origin() != OperationOrigin.USER) {
            log.debug("Ignoring non-USER deposit event. walletId={}, origin={}", event.walletId(), event.origin());
            return;
        }

        final List<SavingsPlan> activePlans = savingsPlanDao.findActiveBySourceWalletId(event.walletId());
        if (activePlans.isEmpty()) {
            return;
        }

        final var currentBalance = balanceUseCase.getBalance(event.walletId());

        for (final SavingsPlan plan : activePlans) {
            final List<IntendedSweepAction> actions = ruleEngine.evaluateDeposit(plan, event.amount(), currentBalance);
            for (final IntendedSweepAction action : actions) {
                executionService.executeSweep(action, event.operationId(), "DEPOSIT_COMPLETED");
            }
        }
    }

    @ApplicationModuleListener
    public void onTransfer(@NonNull final TransferCompletedEvent event) {
        log.info("onTransfer({})", event);
        Objects.requireNonNull(event, "event cannot be null");

        // O(1) Loop Prevention (I-SAVINGS-001)
        if (event.origin() != OperationOrigin.USER) {
            log.debug("Ignoring non-USER transfer event. from={}, origin={}", event.from(), event.origin());
            return;
        }

        List<SavingsPlan> activePlans = savingsPlanDao.findActiveBySourceWalletId(event.from());
        if (activePlans.isEmpty()) {
            return;
        }

        BigDecimal currentBalance = balanceUseCase.getBalance(event.from());

        for (SavingsPlan plan : activePlans) {
            List<IntendedSweepAction> actions = ruleEngine.evaluateTransfer(plan, event.amount(), currentBalance);
            for (IntendedSweepAction action : actions) {
                executionService.executeSweep(action, event.operationId(), "TRANSFER_COMPLETED");
            }
        }
    }
}
