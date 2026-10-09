package br.com.wallet.copilot;

import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.copilot.internal.reconciliation.ProposalLeaseReconciler;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.DownstreamStatus;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.ExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalLeaseReconciler Unit Tests (TASK-4.9, I-AI-001, I-AI-010)")
class ProposalLeaseReconcilerTest {

    @Mock
    private ProposalDao proposalDao;

    @Mock
    private DownstreamExecutionBridge bridge;

    private ProposalLeaseReconciler reconciler;

    private final UUID proposalId = UUID.randomUUID();
    private final UUID executionOpId = UUID.randomUUID();
    private final String tenantId = "tenant-reconcile";

    @BeforeEach
    void setUp() {
        reconciler = new ProposalLeaseReconciler(proposalDao, bridge);
    }

    private FinancialProposal buildExecutingProposal() {
        Instant now = Instant.now();
        return new FinancialProposal(
                proposalId,
                tenantId,
                UUID.randomUUID(),
                ProposalType.TRANSFER,
                "{\"amount\":100}",
                "hash-123",
                ProposalStatus.EXECUTING,
                "key-1",
                executionOpId.toString(),
                "agent-1",
                "human-mgr",
                now.minus(5, ChronoUnit.MINUTES),
                now.plus(10, ChronoUnit.MINUTES),
                now.minus(4, ChronoUnit.MINUTES),
                now.minus(1, ChronoUnit.MINUTES), // lease expired
                now.minus(4, ChronoUnit.MINUTES),
                null,
                null
        );
    }

    @Test
    @DisplayName("I-AI-006: Downstream COMPLETED -> mark EXECUTED without repeating mutation")
    void shouldReconcileCompletedDownstreamWithoutReexecuting() {
        FinancialProposal proposal = buildExecutingProposal();
        when(bridge.checkStatus(ProposalType.TRANSFER, executionOpId))
                .thenReturn(DownstreamStatus.COMPLETED);
        when(proposalDao.findById(proposalId, tenantId))
                .thenReturn(Optional.of(proposal));

        FinancialProposal result = reconciler.reconcileStaleProposal(proposal);

        verify(proposalDao).markExecuted(eq(proposalId), eq(tenantId), any(), eq(executionOpId.toString()));
        verify(bridge, never()).dispatch(any());
    }

    @Test
    @DisplayName("I-AI-001: Downstream NOT_EXECUTED -> safely re-dispatch using existing human authorization")
    void shouldReexecuteDownstreamWhenNotExecuted() {
        FinancialProposal proposal = buildExecutingProposal();
        when(bridge.checkStatus(ProposalType.TRANSFER, executionOpId))
                .thenReturn(DownstreamStatus.NOT_EXECUTED);
        when(bridge.dispatch(proposal))
                .thenReturn(new ExecutionResult.Success("tx-ok-reconciled"));
        when(proposalDao.findById(proposalId, tenantId))
                .thenReturn(Optional.of(proposal));

        FinancialProposal result = reconciler.reconcileStaleProposal(proposal);

        verify(proposalDao).renewLease(eq(proposalId), eq(tenantId), any());
        verify(bridge).dispatch(proposal);
        verify(proposalDao).markExecuted(eq(proposalId), eq(tenantId), any(), eq("tx-ok-reconciled"));
    }

    @Test
    @DisplayName("I-AI-010: Re-execution business failure -> mark INVALIDATED")
    void shouldInvalidateOnBusinessFailure() {
        FinancialProposal proposal = buildExecutingProposal();
        when(bridge.checkStatus(ProposalType.TRANSFER, executionOpId))
                .thenReturn(DownstreamStatus.NOT_EXECUTED);
        when(bridge.dispatch(proposal))
                .thenReturn(new ExecutionResult.BusinessFailure("Account blocked"));
        when(proposalDao.findById(proposalId, tenantId))
                .thenReturn(Optional.of(proposal));

        FinancialProposal result = reconciler.reconcileStaleProposal(proposal);

        verify(proposalDao).markInvalidated(eq(proposalId), eq(tenantId), any());
    }

    @Test
    @DisplayName("I-AI-010: Downstream status UNKNOWN (timeout) -> extend lease, preserve EXECUTING")
    void shouldPreserveExecutingOnUnknownStatus() {
        FinancialProposal proposal = buildExecutingProposal();
        when(bridge.checkStatus(ProposalType.TRANSFER, executionOpId))
                .thenReturn(DownstreamStatus.UNKNOWN);

        FinancialProposal result = reconciler.reconcileStaleProposal(proposal);

        verify(proposalDao).renewLease(eq(proposalId), eq(tenantId), any());
        verify(proposalDao, never()).markExecuted(any(), any(), any(), any());
        verify(proposalDao, never()).markInvalidated(any(), any(), any());
        verify(bridge, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should scan and reconcile all stale leases")
    void shouldReconcileAllStaleLeases() {
        FinancialProposal p1 = buildExecutingProposal();
        when(proposalDao.findStaleExecutingLeases(any()))
                .thenReturn(List.of(p1));
        when(bridge.checkStatus(any(), any()))
                .thenReturn(DownstreamStatus.COMPLETED);
        when(proposalDao.findById(any(), any()))
                .thenReturn(Optional.of(p1));

        reconciler.reconcileAllStaleLeases();

        verify(proposalDao).markExecuted(eq(p1.id()), eq(tenantId), any(), any());
    }
}
