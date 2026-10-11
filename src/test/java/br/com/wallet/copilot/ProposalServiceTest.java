package br.com.wallet.copilot;

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
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.copilot.internal.reconciliation.ProposalLeaseReconciler;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge;
import br.com.wallet.copilot.internal.service.DownstreamExecutionBridge.ExecutionResult;
import br.com.wallet.copilot.internal.service.ProposalService;
import br.com.wallet.copilot.internal.util.ParametersHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProposalService Unit Tests (TASK-4.7, TASK-4.8, I-AI-001, I-AI-005, I-AI-006, I-AI-008, I-AI-010)")
class ProposalServiceTest {

    @Mock
    private ProposalDao proposalDao;

    @Mock
    private DownstreamExecutionBridge bridge;

    @Mock
    private ProposalLeaseReconciler reconciler;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProposalService service;

    private final String tenantId = "tenant-svc";
    private final String creator = "agent-1";
    private final String approver = "human-mgr";
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProposalService(proposalDao, bridge, reconciler, eventPublisher);
    }

    private FinancialProposal buildProposal(UUID id, String idempotencyKey, String params, ProposalStatus status, Instant expiresAt) {
        Instant now = Instant.now();
        return new FinancialProposal(
                id,
                tenantId,
                walletId,
                ProposalType.TRANSFER,
                params,
                ParametersHashUtil.computeHash(params),
                status,
                idempotencyKey,
                UUID.randomUUID().toString(),
                creator,
                null,
                now.minus(1, ChronoUnit.MINUTES),
                expiresAt != null ? expiresAt : now.plus(15, ChronoUnit.MINUTES),
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("REQ-COPILOT-003 / I-AI-008: Create proposal returns new proposal when key is new")
    void shouldCreateNewProposal() {
        String params = "{\"amount\":100.00,\"to\":\"" + UUID.randomUUID() + "\"}";
        CreateProposalCommand cmd = new CreateProposalCommand(walletId, ProposalType.TRANSFER, params, "idem-1");

        when(proposalDao.findByTenantAndIdempotencyKey(tenantId, "idem-1")).thenReturn(Optional.empty());
        when(proposalDao.insert(any())).thenReturn(true);

        ProposalResponse response = service.createProposal(tenantId, creator, cmd);

        assertThat(response).isNotNull();
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.status()).isEqualTo(ProposalStatus.PROPOSED);
        verify(proposalDao).insert(any());
        verify(eventPublisher).publishEvent(any(ProposalCreatedEvent.class));
    }

    @Test
    @DisplayName("I-AI-008: Replay with identical parameters returns existing proposal")
    void shouldReturnExistingProposalOnIdempotentReplay() {
        String params = "{\"amount\":100.00,\"to\":\"" + UUID.randomUUID() + "\"}";
        CreateProposalCommand cmd = new CreateProposalCommand(walletId, ProposalType.TRANSFER, params, "idem-1");
        FinancialProposal existing = buildProposal(UUID.randomUUID(), "idem-1", params, ProposalStatus.PROPOSED, null);

        when(proposalDao.findByTenantAndIdempotencyKey(tenantId, "idem-1")).thenReturn(Optional.of(existing));

        ProposalResponse response = service.createProposal(tenantId, creator, cmd);

        assertThat(response.id()).isEqualTo(existing.id());
        verify(proposalDao, never()).insert(any());
    }

    @Test
    @DisplayName("I-AI-008: Replay with conflicting parameters throws IdempotencyConflictException")
    void shouldThrowConflictWhenParametersDiffer() {
        String params1 = "{\"amount\":100.00,\"to\":\"" + UUID.randomUUID() + "\"}";
        String params2 = "{\"amount\":200.00,\"to\":\"" + UUID.randomUUID() + "\"}";
        CreateProposalCommand cmd = new CreateProposalCommand(walletId, ProposalType.TRANSFER, params2, "idem-1");
        FinancialProposal existing = buildProposal(UUID.randomUUID(), "idem-1", params1, ProposalStatus.PROPOSED, null);

        when(proposalDao.findByTenantAndIdempotencyKey(tenantId, "idem-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createProposal(tenantId, creator, cmd))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("Should reject proposal creation when parameters are invalid")
    void shouldRejectProposalCreationWhenParametersAreInvalid() {
        String invalidParams = "{\"name\":\"\"}";
        CreateProposalCommand cmd = new CreateProposalCommand(walletId, ProposalType.GOAL_ADJUSTMENT, invalidParams, "key-invalid");

        doThrow(new IllegalArgumentException("Missing required parameter: goalId"))
                .when(bridge).validateParameters(ProposalType.GOAL_ADJUSTMENT, invalidParams);

        assertThatThrownBy(() -> service.createProposal(tenantId, creator, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goalId");

        verify(proposalDao, never()).insert(any());
    }

    @Test
    @DisplayName("REQ-COPILOT-006 / I-AI-005: Synchronous authoritative TTL gate rejects expired proposal")
    void shouldRejectApprovalWhenExpired() {
        UUID id = UUID.randomUUID();
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        FinancialProposal proposal = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.PROPOSED, past);

        when(proposalDao.findById(id, tenantId)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.approveProposal(tenantId, approver, new ApproveProposalCommand(id)))
                .isInstanceOf(ProposalExpiredException.class);
        verify(proposalDao).markExpired(id, tenantId);
    }

    @Test
    @DisplayName("REQ-COPILOT-008 / I-AI-006: Approving already EXECUTED proposal returns cached response")
    void shouldReturnCachedWhenAlreadyExecuted() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.EXECUTED, null);

        when(proposalDao.findById(id, tenantId)).thenReturn(Optional.of(proposal));

        ProposalResponse response = service.approveProposal(tenantId, approver, new ApproveProposalCommand(id));

        assertThat(response.status()).isEqualTo(ProposalStatus.EXECUTED);
        verify(bridge, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should reject approval when proposal is in terminal state")
    void shouldRejectApprovalInTerminalState() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.REJECTED, null);

        when(proposalDao.findById(id, tenantId)).thenReturn(Optional.of(proposal));

        assertThatThrownBy(() -> service.approveProposal(tenantId, approver, new ApproveProposalCommand(id)))
                .isInstanceOf(ProposalConflictException.class);
    }

    @Test
    @DisplayName("I-AI-001 / I-AI-005: Atomic claim and successful downstream execution")
    void shouldClaimAndExecuteSuccessfully() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposed = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.PROPOSED, null);
        FinancialProposal executing = new FinancialProposal(
                id, tenantId, walletId, ProposalType.TRANSFER, proposed.parametersJson(), proposed.parametersHash(),
                ProposalStatus.EXECUTING, "idem-1", proposed.executionOperationId(), creator, approver,
                proposed.createdAt(), proposed.expiresAt(), Instant.now(), Instant.now().plus(2, ChronoUnit.MINUTES),
                Instant.now(), null, null
        );
        FinancialProposal executed = new FinancialProposal(
                id, tenantId, walletId, ProposalType.TRANSFER, proposed.parametersJson(), proposed.parametersHash(),
                ProposalStatus.EXECUTED, "idem-1", proposed.executionOperationId(), creator, approver,
                proposed.createdAt(), proposed.expiresAt(), Instant.now(), Instant.now().plus(2, ChronoUnit.MINUTES),
                Instant.now(), Instant.now(), "tx-ref-ok"
        );

        when(proposalDao.findById(id, tenantId))
                .thenReturn(Optional.of(proposed))
                .thenReturn(Optional.of(executing))
                .thenReturn(Optional.of(executed));
        when(proposalDao.claimForExecution(eq(id), eq(tenantId), eq(approver), any(), any()))
                .thenReturn(true);
        when(bridge.dispatch(any())).thenReturn(new ExecutionResult.Success("tx-ref-ok"));

        ProposalResponse response = service.approveProposal(tenantId, approver, new ApproveProposalCommand(id));

        assertThat(response.status()).isEqualTo(ProposalStatus.EXECUTED);
        verify(proposalDao).markExecuted(eq(id), eq(tenantId), any(), eq("tx-ref-ok"));
        verify(eventPublisher).publishEvent(any(ProposalApprovedEvent.class));
        verify(eventPublisher).publishEvent(any(ProposalExecutedEvent.class));
    }

    @Test
    @DisplayName("I-AI-010: Business failure transitions proposal to INVALIDATED")
    void shouldInvalidateProposalOnBusinessFailure() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposed = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.PROPOSED, null);
        FinancialProposal executing = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.EXECUTING, null);

        when(proposalDao.findById(id, tenantId))
                .thenReturn(Optional.of(proposed))
                .thenReturn(Optional.of(executing));
        when(proposalDao.claimForExecution(eq(id), eq(tenantId), eq(approver), any(), any()))
                .thenReturn(true);
        when(bridge.dispatch(any())).thenReturn(new ExecutionResult.BusinessFailure("Insufficient balance"));

        assertThatThrownBy(() -> service.approveProposal(tenantId, approver, new ApproveProposalCommand(id)))
                .isInstanceOf(ProposalConflictException.class)
                .hasMessageContaining("Insufficient balance");

        verify(proposalDao).markInvalidated(eq(id), eq(tenantId), any());
    }

    @Test
    @DisplayName("I-AI-010: Technical failure preserves proposal in EXECUTING under lease")
    void shouldPreserveExecutingOnTechnicalFailure() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposed = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.PROPOSED, null);
        FinancialProposal executing = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.EXECUTING, null);

        when(proposalDao.findById(id, tenantId))
                .thenReturn(Optional.of(proposed))
                .thenReturn(Optional.of(executing));
        when(proposalDao.claimForExecution(eq(id), eq(tenantId), eq(approver), any(), any()))
                .thenReturn(true);
        when(bridge.dispatch(any())).thenReturn(new ExecutionResult.TechnicalFailure(new RuntimeException("DB timeout")));

        assertThatThrownBy(() -> service.approveProposal(tenantId, approver, new ApproveProposalCommand(id)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Technical failure");

        // Must NOT be marked invalidated!
        verify(proposalDao, never()).markInvalidated(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject proposal when status is PROPOSED")
    void shouldRejectProposal() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposed = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.PROPOSED, null);
        FinancialProposal rejected = buildProposal(id, "idem-1", "{\"amount\":50}", ProposalStatus.REJECTED, null);

        when(proposalDao.findById(id, tenantId))
                .thenReturn(Optional.of(proposed))
                .thenReturn(Optional.of(rejected));
        when(proposalDao.markRejected(eq(id), eq(tenantId), eq(approver), any())).thenReturn(true);

        ProposalResponse response = service.rejectProposal(tenantId, approver, new RejectProposalCommand(id, "Too high"));
        assertThat(response.status()).isEqualTo(ProposalStatus.REJECTED);
    }
}
