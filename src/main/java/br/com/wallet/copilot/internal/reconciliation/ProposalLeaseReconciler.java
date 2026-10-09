package br.com.wallet.copilot.internal.reconciliation;

import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.DownstreamStatus;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.ExecutionResult;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class ProposalLeaseReconciler {

    private static final Logger log = LoggerFactory.getLogger(ProposalLeaseReconciler.class);

    private final ProposalDao proposalDao;
    private final DownstreamExecutionBridge bridge;

    @Autowired
    public ProposalLeaseReconciler(@NonNull final ProposalDao proposalDao,
                                   @NonNull final DownstreamExecutionBridge bridge) {
        this.proposalDao = Objects.requireNonNull(proposalDao, "proposalDao cannot be null");
        this.bridge = Objects.requireNonNull(bridge, "bridge cannot be null");
    }

    public void reconcileAllStaleLeases() {
        Instant now = Instant.now();
        List<FinancialProposal> staleProposals = proposalDao.findStaleExecutingLeases(now);
        for (FinancialProposal proposal : staleProposals) {
            try {
                reconcileStaleProposal(proposal);
            } catch (Exception e) {
                log.error("Failed to reconcile stale proposal {}: {}", proposal.id(), e.getMessage(), e);
            }
        }
    }

    @NonNull
    public FinancialProposal reconcileStaleProposal(@NonNull final FinancialProposal proposal) {
        Objects.requireNonNull(proposal, "proposal cannot be null");
        UUID opId = UUID.fromString(proposal.executionOperationId());
        DownstreamStatus downstreamStatus = bridge.checkStatus(proposal.type(), opId);

        log.info("Reconciling proposal {} with downstream status {}", proposal.id(), downstreamStatus);

        switch (downstreamStatus) {
            case COMPLETED -> {
                // Downstream completed: update to EXECUTED with cached reference, zero duplicate mutations
                proposalDao.markExecuted(proposal.id(), proposal.tenantId(), Instant.now(), proposal.executionOperationId());
                return proposalDao.findById(proposal.id(), proposal.tenantId()).orElse(proposal);
            }
            case NOT_EXECUTED -> {
                // Downstream provably not executed: renew lease and safely re-dispatch using existing human authorization
                Instant newLease = Instant.now().plus(2, ChronoUnit.MINUTES);
                proposalDao.renewLease(proposal.id(), proposal.tenantId(), newLease);

                ExecutionResult result = bridge.dispatch(proposal);
                if (result instanceof ExecutionResult.Success(String executionReference)) {
                    proposalDao.markExecuted(proposal.id(), proposal.tenantId(), Instant.now(), executionReference);
                } else if (result instanceof ExecutionResult.BusinessFailure failure) {
                    proposalDao.markInvalidated(proposal.id(), proposal.tenantId(), Instant.now());
                } else {
                    log.warn("Re-dispatch for proposal {} encountered technical failure; preserved in EXECUTING", proposal.id());
                }
                return proposalDao.findById(proposal.id(), proposal.tenantId()).orElse(proposal);
            }
            case UNKNOWN -> {
                // Transient query failure or downstream unavailable: extend lease to retry next cycle
                proposalDao.renewLease(proposal.id(), proposal.tenantId(), Instant.now().plus(1, ChronoUnit.MINUTES));
                return proposal;
            }
        }
        return proposal;
    }
}
