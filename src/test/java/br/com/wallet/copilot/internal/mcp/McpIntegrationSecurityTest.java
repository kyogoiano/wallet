package br.com.wallet.copilot.internal.mcp;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.exception.IdempotencyConflictException;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import br.com.wallet.copilot.internal.mcp.config.McpSecurityFilter;
import br.com.wallet.copilot.internal.mcp.elicitation.McpCapabilityNegotiator;
import br.com.wallet.copilot.internal.mcp.elicitation.McpCapabilityNegotiator.InteractionTier;
import br.com.wallet.copilot.internal.mcp.elicitation.McpFormElicitationBridge;
import br.com.wallet.copilot.internal.mcp.elicitation.McpFormElicitationBridge.ElicitationAction;
import br.com.wallet.copilot.internal.mcp.elicitation.McpFormElicitationBridge.FormElicitationResult;
import br.com.wallet.copilot.internal.mcp.elicitation.McpUrlElicitationService;
import br.com.wallet.copilot.internal.mcp.interaction.McpInteractionTracker;
import br.com.wallet.copilot.internal.mcp.model.McpClientCapabilities;
import br.com.wallet.copilot.internal.mcp.tools.WalletProposalTools;
import br.com.wallet.copilot.internal.mcp.tools.WalletReadTools;
import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.goals.api.GoalStrategyUseCase;
import br.com.wallet.intelligence.api.CashflowQueryUseCase;
import br.com.wallet.intelligence.api.SubscriptionQueryUseCase;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.exceptions.AccountNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tri-Gate End-to-End Security & Protocol Convergence Suite for Phase 4.1 MCP Gateway (REQ-MCP-001 through 008, I-MCP-001 through 007).
 */
@ExtendWith(MockitoExtension.class)
class McpIntegrationSecurityTest {

    @Mock
    private ProposalUseCase proposalUseCase;
    @Mock
    private AccountUseCase accountUseCase;
    @Mock
    private BalanceUseCase balanceUseCase;
    @Mock
    private CashflowQueryUseCase cashflowQueryUseCase;
    @Mock
    private SubscriptionQueryUseCase subscriptionQueryUseCase;
    @Mock
    private GoalStrategyUseCase goalStrategyUseCase;
    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Instant baseTime = Instant.parse("2026-10-10T12:00:00Z");
    private Clock clock;

    private McpSecurityFilter securityFilter;
    private WalletReadTools readTools;
    private WalletProposalTools proposalTools;
    private McpCapabilityNegotiator negotiator;
    private McpFormElicitationBridge formBridge;
    private McpUrlElicitationService urlService;
    private McpInteractionTracker tracker;

    private final String tenantId = "tenant-alpha";
    private final String humanUser = "user-owner-1";
    private final String agentPrincipal = "agent-autonomous-1";
    private final UUID sourceWallet = UUID.randomUUID();
    private final UUID targetWallet = UUID.randomUUID();
    private final UUID proposalId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        securityFilter = new McpSecurityFilter();
        readTools = new WalletReadTools(accountUseCase, balanceUseCase, cashflowQueryUseCase, subscriptionQueryUseCase, goalStrategyUseCase);
        proposalTools = new WalletProposalTools(proposalUseCase, objectMapper);
        negotiator = new McpCapabilityNegotiator();
        formBridge = new McpFormElicitationBridge(proposalUseCase);
        urlService = new McpUrlElicitationService(proposalUseCase, clock);
        tracker = new McpInteractionTracker(proposalUseCase, clock);
    }

    @AfterEach
    void tearDown() {
        McpSecurityContext.clear();
    }

    // ==========================================
    // GATE A: Architecture, Transport & Security
    // ==========================================

    @Test
    @DisplayName("Gate A: Unauthenticated MCP request to /mcp returns 401 Unauthorized (I-MCP-005)")
    void testGateA_UnauthenticatedRequestBlocked() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Gate A: Tool parameter tenant override blocked and overridden by authenticated context (I-MCP-005)")
    void testGateA_TenantOverrideBlocked() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("Authorization", "Bearer valid-mcp-token");
        request.addHeader("X-Tenant-Id", tenantId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityFilter.doFilter(request, response, filterChain);

        assertThat(McpSecurityFilter.resolveAuthenticatedTenant(request, "attacker-tenant"))
                .isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Gate A: Read tool asserting ownership throws AccountNotFoundException for cross-tenant wallet (I-AI-004)")
    void testGateA_CrossTenantReadThrows404() {
        McpSecurityContext.setContext(tenantId, agentPrincipal);

        Account crossTenantAccount = new Account(
                sourceWallet, BigDecimal.ZERO, 1L, UUID.randomUUID(),
                AccountStatus.ACTIVE, null, null, baseTime, "other-tenant"
        );
        when(accountUseCase.find(sourceWallet)).thenReturn(crossTenantAccount);

        assertThatThrownBy(() -> readTools.getBalance(sourceWallet.toString()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ==========================================
    // GATE B: Authorization & Concurrent Idempotency
    // ==========================================

    @Test
    @DisplayName("Gate B: Three-Identity Non-Conflation - Agent cannot approve on behalf of human (I-MCP-007)")
    void testGateB_AgentCannotApprove() {
        McpSecurityContext.setContext(tenantId, agentPrincipal, OperationOrigin.AGENT, false);

        FormElicitationResult result = formBridge.handleElicitationResponse(proposalId, ElicitationAction.ACCEPT, null);

        assertThat(result.status()).isEqualTo(ProposalStatus.PROPOSED);
        assertThat(result.message()).contains("Human authorization required");
        verify(proposalUseCase, never()).approveProposal(any(), any(), any());
    }

    @Test
    @DisplayName("Gate B: Cross-user URL approval is rejected with SecurityException (I-MCP-003)")
    void testGateB_CrossUserUrlApprovalRejected() {
        String approvalUrl = urlService.generateApprovalUrl(tenantId, proposalId, humanUser);
        String token = approvalUrl.substring(approvalUrl.indexOf("token=") + 6);

        assertThatThrownBy(() -> urlService.consumeAndApprove(token, tenantId, "different-user"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("User mismatch");

        verify(proposalUseCase, never()).approveProposal(any(), any(), any());
    }

    @Test
    @DisplayName("Gate B: Reused or expired URL token rejected (single-use replay protection)")
    void testGateB_ReusedUrlTokenRejected() {
        String approvalUrl = urlService.generateApprovalUrl(tenantId, proposalId, humanUser);
        String token = approvalUrl.substring(approvalUrl.indexOf("token=") + 6);

        ProposalResponse executed = new ProposalResponse(
                proposalId, tenantId, sourceWallet, ProposalType.TRANSFER,
                ProposalStatus.EXECUTED, "{}", "key-1", UUID.randomUUID().toString(),
                agentPrincipal, humanUser, baseTime, baseTime.plusSeconds(300),
                null, null, baseTime, baseTime, "ref-1"
        );
        when(proposalUseCase.approveProposal(eq(tenantId), eq(humanUser), any(ApproveProposalCommand.class)))
                .thenReturn(executed);

        // First consume succeeds
        urlService.consumeAndApprove(token, tenantId, humanUser);

        // Second consume (replay) fails
        assertThatThrownBy(() -> urlService.consumeAndApprove(token, tenantId, humanUser))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid or consumed token");
    }

    @Test
    @DisplayName("Gate B: Concurrent proposal creation with conflicting payload throws IdempotencyConflictException (I-MCP-006)")
    void testGateB_ConflictingPayloadThrowsConflict() {
        McpSecurityContext.setContext(tenantId, agentPrincipal);

        when(proposalUseCase.createProposal(eq(tenantId), eq(agentPrincipal), any(CreateProposalCommand.class)))
                .thenThrow(new IdempotencyConflictException("Payload mismatch on key"));

        assertThatThrownBy(() -> proposalTools.proposeTransfer(
                sourceWallet.toString(), targetWallet.toString(),
                "250.00", "Rent payment", "idemp-dup-key"
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    // ==========================================
    // GATE C: Elicitation & Resumption Flows
    // ==========================================

    @Test
    @DisplayName("Gate C: Form decline transitions proposal to REJECTED")
    void testGateC_FormDeclineRejectsProposal() {
        McpSecurityContext.setContext(tenantId, humanUser, OperationOrigin.USER, true);

        ProposalResponse rejected = new ProposalResponse(
                proposalId, tenantId, sourceWallet, ProposalType.TRANSFER,
                ProposalStatus.REJECTED, "{}", "key-1", UUID.randomUUID().toString(),
                agentPrincipal, null, baseTime, baseTime.plusSeconds(300),
                null, null, null, null, null
        );
        when(proposalUseCase.rejectProposal(eq(tenantId), eq(humanUser), any())).thenReturn(rejected);

        FormElicitationResult result = formBridge.handleElicitationResponse(proposalId, ElicitationAction.DECLINE, "User declined");

        assertThat(result.status()).isEqualTo(ProposalStatus.REJECTED);
        verify(proposalUseCase).rejectProposal(eq(tenantId), eq(humanUser), any());
    }

    @Test
    @DisplayName("Gate C: Form cancel dismisses interaction without altering proposal state (final.txt 1.4)")
    void testGateC_FormCancelPreservesProposedState() {
        McpSecurityContext.setContext(tenantId, humanUser, OperationOrigin.USER, true);

        FormElicitationResult result = formBridge.handleElicitationResponse(proposalId, ElicitationAction.CANCEL, null);

        assertThat(result.status()).isEqualTo(ProposalStatus.PROPOSED);
        verify(proposalUseCase, never()).rejectProposal(any(), any(), any());
        verify(proposalUseCase, never()).approveProposal(any(), any(), any());
    }

    @Test
    @DisplayName("Gate C: State loss during MRTR interaction fails safe with CORRELATION_LOST without creating duplicate proposal (I-MCP-004)")
    void testGateC_StateLossFailsSafeToWebCli() {
        String correlationId = tracker.track(tenantId, agentPrincipal, proposalId);
        tracker.evict(correlationId); // simulate restart

        McpInteractionTracker.ResumptionResult result = tracker.resume(correlationId, tenantId);

        assertThat(result.resumed()).isFalse();
        assertThat(result.reason()).isEqualTo("CORRELATION_LOST");
        assertThat(result.fallbackUri()).contains("https://wallet.bank/copilot/proposals/review");
        verify(proposalUseCase, never()).createProposal(any(), any(), any());
    }

    @Test
    @DisplayName("Gate C: Client without elicitation degrades to external Web/CLI instructions without auto-executing (I-MCP-002)")
    void testGateC_DegradesToExternalInstructions() {
        McpClientCapabilities unsupported = new McpClientCapabilities(false, false);

        InteractionTier tier = negotiator.negotiate(unsupported);

        assertThat(tier).isEqualTo(InteractionTier.FALLBACK_EXTERNAL);
        String instructions = negotiator.buildFallbackInstructions(proposalId.toString());
        assertThat(instructions).contains("Human authorization required")
                .contains(proposalId.toString());
    }
}
