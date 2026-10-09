package br.com.wallet.copilot.internal.service;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import br.com.wallet.copilot.api.event.ProposalApprovedEvent;
import br.com.wallet.copilot.api.event.ProposalCreatedEvent;
import br.com.wallet.copilot.api.event.ProposalExecutedEvent;
import br.com.wallet.copilot.api.exception.IdempotencyConflictException;
import br.com.wallet.copilot.api.exception.ProposalConflictException;
import br.com.wallet.copilot.api.exception.ProposalExpiredException;
import br.com.wallet.copilot.api.exception.ProposalNotFoundException;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.copilot.internal.reconciliation.ProposalLeaseReconciler;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.ExecutionResult;
import br.com.wallet.copilot.internal.util.ParametersHashUtil;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProposalService implements ProposalUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final ProposalDao proposalDao;
    private final DownstreamExecutionBridge bridge;
    private final ProposalLeaseReconciler reconciler;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ProposalService(@NonNull final ProposalDao proposalDao,
                           @NonNull final DownstreamExecutionBridge bridge,
                           @NonNull final ProposalLeaseReconciler reconciler,
                           @NonNull final ApplicationEventPublisher eventPublisher) {
        this.proposalDao = Objects.requireNonNull(proposalDao, "proposalDao cannot be null");
        this.bridge = Objects.requireNonNull(bridge, "bridge cannot be null");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
    }

    @Override
    @NonNull
    public ProposalResponse createProposal(@NonNull final String tenantId,
                                           @NonNull final String createdBy,
                                           @NonNull final CreateProposalCommand command) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(createdBy, "createdBy cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        bridge.validateParameters(command.type(), command.parametersJson());

        String parametersHash = ParametersHashUtil.computeHash(command.parametersJson());

        // Check if existing proposal exists for (tenantId, idempotencyKey)
        Optional<FinancialProposal> existing = proposalDao.findByTenantAndIdempotencyKey(tenantId, command.idempotencyKey());
        if (existing.isPresent()) {
            FinancialProposal proposal = existing.get();
            if (proposal.parametersHash().equals(parametersHash)) {
                log.info("Returning existing proposal {} for idempotent key {}", proposal.id(), command.idempotencyKey());
                return proposal.toResponse();
            } else {
                throw new IdempotencyConflictException(
                        "Proposal already exists with idempotencyKey " + command.idempotencyKey() + " but conflicting parameters"
                );
            }
        }

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String executionOpId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(15, ChronoUnit.MINUTES);

        FinancialProposal newProposal = new FinancialProposal(
                id,
                tenantId,
                command.walletId(),
                command.type(),
                command.parametersJson(),
                parametersHash,
                ProposalStatus.PROPOSED,
                command.idempotencyKey(),
                executionOpId,
                createdBy,
                null,
                now,
                expiresAt,
                null,
                null,
                null,
                null,
                null
        );

        boolean inserted = proposalDao.insert(newProposal);
        if (!inserted) {
            // Concurrency race on insert: re-fetch and validate hash
            FinancialProposal concurrent = proposalDao.findByTenantAndIdempotencyKey(tenantId, command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Failed to insert proposal and unable to retrieve concurrent record"));
            if (concurrent.parametersHash().equals(parametersHash)) {
                return concurrent.toResponse();
            } else {
                throw new IdempotencyConflictException("Proposal already exists with conflicting parameters");
            }
        }

        eventPublisher.publishEvent(new ProposalCreatedEvent(id, tenantId, command.walletId(), command.type(), now));
        return newProposal.toResponse();
    }

    @Override
    @NonNull
    public ProposalResponse approveProposal(@NonNull final String tenantId,
                                            @NonNull final String approvedBy,
                                            @NonNull final ApproveProposalCommand command) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(approvedBy, "approvedBy cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        FinancialProposal proposal = proposalDao.findById(command.proposalId(), tenantId)
                .orElseThrow(() -> new ProposalNotFoundException("Proposal not found: " + command.proposalId()));

        if (proposal.status() == ProposalStatus.EXECUTED) {
            log.info("Proposal {} already EXECUTED, returning cached result", proposal.id());
            return proposal.toResponse();
        }

        if (proposal.status() == ProposalStatus.REJECTED
                || proposal.status() == ProposalStatus.EXPIRED
                || proposal.status() == ProposalStatus.INVALIDATED) {
            throw new ProposalConflictException("Proposal is in terminal state: " + proposal.status());
        }

        Instant now = Instant.now();

        if (proposal.status() == ProposalStatus.EXECUTING) {
            if (!proposal.isLeaseExpired(now)) {
                throw new ProposalConflictException("Proposal is currently executing under active lease");
            }
            log.info("Proposal {} has stale lease in EXECUTING; invoking reconciler", proposal.id());
            FinancialProposal reconciled = reconciler.reconcileStaleProposal(proposal);
            return reconciled.toResponse();
        }

        // Must be PROPOSED: enforce synchronous authoritative TTL gate
        if (proposal.isExpired(now)) {
            proposalDao.markExpired(proposal.id(), tenantId);
            throw new ProposalExpiredException("Proposal expired at " + proposal.expiresAt());
        }

        // Atomic conditional execution claim with lease
        Instant leaseUntil = now.plus(2, ChronoUnit.MINUTES);
        boolean claimed = proposalDao.claimForExecution(proposal.id(), tenantId, approvedBy, now, leaseUntil);
        if (!claimed) {
            FinancialProposal current = proposalDao.findById(proposal.id(), tenantId)
                    .orElseThrow(() -> new ProposalNotFoundException("Proposal not found"));
            if (current.status() == ProposalStatus.EXECUTED) {
                return current.toResponse();
            }
            throw new ProposalConflictException("Proposal was claimed or modified by a concurrent request");
        }

        eventPublisher.publishEvent(new ProposalApprovedEvent(proposal.id(), tenantId, approvedBy, now));

        FinancialProposal executingProposal = proposalDao.findById(proposal.id(), tenantId)
                .orElseThrow(() -> new IllegalStateException("Claimed proposal not found"));

        ExecutionResult result = bridge.dispatch(executingProposal);

        switch (result) {
            case ExecutionResult.Success(String executionReference) -> {
                Instant executedAt = Instant.now();
                proposalDao.markExecuted(proposal.id(), tenantId, executedAt, executionReference);
                eventPublisher.publishEvent(new ProposalExecutedEvent(proposal.id(), tenantId, executionReference, executedAt));
                return proposalDao.findById(proposal.id(), tenantId).orElseThrow().toResponse();
            }
            case ExecutionResult.BusinessFailure(String reason) -> {
                proposalDao.markInvalidated(proposal.id(), tenantId, Instant.now());
                throw new ProposalConflictException("Proposal invalidated due to business failure: " + reason);
            }
            case ExecutionResult.TechnicalFailure(Throwable cause) -> {
                log.warn("Technical failure during execution of proposal {}; preserving in EXECUTING under lease", proposal.id(), cause);
                throw new RuntimeException("Technical failure during downstream dispatch; proposal preserved in EXECUTING", cause);
            }
            default -> {
            }
        }

        return executingProposal.toResponse();
    }

    @Override
    @NonNull
    public ProposalResponse rejectProposal(@NonNull final String tenantId,
                                           @NonNull final String rejectedBy,
                                           @NonNull final RejectProposalCommand command) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(rejectedBy, "rejectedBy cannot be null");
        Objects.requireNonNull(command, "command cannot be null");

        FinancialProposal proposal = proposalDao.findById(command.proposalId(), tenantId)
                .orElseThrow(() -> new ProposalNotFoundException("Proposal not found: " + command.proposalId()));

        if (proposal.status() != ProposalStatus.PROPOSED) {
            throw new ProposalConflictException("Cannot reject proposal with status: " + proposal.status());
        }

        boolean rejected = proposalDao.markRejected(proposal.id(), tenantId, rejectedBy, Instant.now());
        if (!rejected) {
            throw new ProposalConflictException("Proposal status was modified concurrently");
        }

        return proposalDao.findById(proposal.id(), tenantId).orElseThrow().toResponse();
    }

    @Override
    @NonNull
    public Optional<ProposalResponse> findById(@NonNull final UUID proposalId, @NonNull final String tenantId) {
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return proposalDao.findById(proposalId, tenantId).map(FinancialProposal::toResponse);
    }

    @Override
    @NonNull
    public List<ProposalResponse> listPending(@NonNull final String tenantId, @NonNull final UUID walletId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return proposalDao.findPendingByWalletId(tenantId, walletId).stream()
                .map(FinancialProposal::toResponse)
                .toList();
    }
}
