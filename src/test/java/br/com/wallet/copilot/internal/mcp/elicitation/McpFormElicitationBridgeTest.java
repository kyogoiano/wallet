package br.com.wallet.copilot.internal.mcp.elicitation;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import br.com.wallet.copilot.internal.mcp.elicitation.McpFormElicitationBridge.ElicitationAction;
import br.com.wallet.copilot.internal.mcp.elicitation.McpFormElicitationBridge.FormElicitationResult;
import br.com.wallet.core.context.OperationOrigin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpFormElicitationBridgeTest {

    @Mock
    private ProposalUseCase proposalUseCase;

    private McpFormElicitationBridge bridge;

    private final String tenantId = "tenant-alpha";
    private final String humanOwner = "user-human-1";
    private final String agentPrincipal = "agent-runtime-1";
    private final UUID proposalId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bridge = new McpFormElicitationBridge(proposalUseCase);
    }

    @AfterEach
    void tearDown() {
        McpSecurityContext.clear();
    }

    @Test
    @DisplayName("Positive: Verified human credentials confirm form -> dispatches approveProposal")
    void testVerifiedHumanApprovalSucceeds() {
        // Set security context with verified human authorizer
        McpSecurityContext.setContext(tenantId, humanOwner, OperationOrigin.USER, true);

        ProposalResponse executedResponse = new ProposalResponse(
                proposalId, tenantId, walletId, ProposalType.TRANSFER,
                ProposalStatus.EXECUTED, "{}", "key-1", UUID.randomUUID().toString(),
                agentPrincipal, humanOwner, Instant.now(), Instant.now().plusSeconds(300),
                null, null, Instant.now(), Instant.now(), "tx-ref-123"
        );

        when(proposalUseCase.approveProposal(eq(tenantId), eq(humanOwner), any(ApproveProposalCommand.class)))
                .thenReturn(executedResponse);

        FormElicitationResult result = bridge.handleElicitationResponse(proposalId, ElicitationAction.ACCEPT, null);

        assertThat(result.status()).isEqualTo(ProposalStatus.EXECUTED);
        assertThat(result.message()).contains("authorized successfully");
        verify(proposalUseCase).approveProposal(eq(tenantId), eq(humanOwner), any(ApproveProposalCommand.class));
    }

    @Test
    @DisplayName("Security Gate: Agent accept without verified human credentials does NOT approve (I-MCP-007, testAgentCannotApprove)")
    void testAgentCannotApprove() {
        // Agent transport credentials only: isHumanAuthorizer = false
        McpSecurityContext.setContext(tenantId, agentPrincipal, OperationOrigin.AGENT, false);

        FormElicitationResult result = bridge.handleElicitationResponse(proposalId, ElicitationAction.ACCEPT, null);

        // Must not call approveProposal
        verify(proposalUseCase, never()).approveProposal(any(), any(), any());
        assertThat(result.status()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(result.message()).contains("Human authorization required")
                .contains("https://wallet.bank/copilot/proposals/" + proposalId + "/review");
    }

    @Test
    @DisplayName("Boundary: Form decline explicitly rejects proposal -> transitions to REJECTED")
    void testFormDeclineRejectsProposal() {
        McpSecurityContext.setContext(tenantId, humanOwner, OperationOrigin.USER, true);

        ProposalResponse rejectedResponse = new ProposalResponse(
                proposalId, tenantId, walletId, ProposalType.TRANSFER,
                ProposalStatus.REJECTED, "{}", "key-1", UUID.randomUUID().toString(),
                agentPrincipal, null, Instant.now(), Instant.now().plusSeconds(300),
                null, null, null, null, null
        );

        when(proposalUseCase.rejectProposal(eq(tenantId), eq(humanOwner), any(RejectProposalCommand.class)))
                .thenReturn(rejectedResponse);

        FormElicitationResult result = bridge.handleElicitationResponse(proposalId, ElicitationAction.DECLINE, "Too expensive");

        assertThat(result.status()).isEqualTo(ProposalStatus.REJECTED);
        verify(proposalUseCase).rejectProposal(eq(tenantId), eq(humanOwner), any(RejectProposalCommand.class));
    }

    @Test
    @DisplayName("Boundary: Form cancel dismisses interaction -> keeps proposal in PROPOSED without auto-rejecting (final.txt 1.4)")
    void testFormCancelPreservesProposedStatus() {
        McpSecurityContext.setContext(tenantId, humanOwner, OperationOrigin.USER, true);

        FormElicitationResult result = bridge.handleElicitationResponse(proposalId, ElicitationAction.CANCEL, null);

        verify(proposalUseCase, never()).rejectProposal(any(), any(), any());
        verify(proposalUseCase, never()).approveProposal(any(), any(), any());
        assertThat(result.status()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(result.message()).contains("Interaction cancelled").contains("Proposal remains pending");
    }
}
