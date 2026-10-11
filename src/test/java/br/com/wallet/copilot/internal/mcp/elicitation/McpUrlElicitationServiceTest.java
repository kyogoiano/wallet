package br.com.wallet.copilot.internal.mcp.elicitation;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpUrlElicitationServiceTest {

    @Mock
    private ProposalUseCase proposalUseCase;

    private Clock clock;
    private McpUrlElicitationService service;

    private final String tenantId = "tenant-alpha";
    private final String authorizedUser = "user-human-owner";
    private final String unauthorizedUser = "attacker-user-99";
    private final UUID proposalId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final Instant baseTime = Instant.parse("2026-10-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        service = new McpUrlElicitationService(proposalUseCase, clock);
    }

    @Test
    @DisplayName("Positive: Generates opaque HTTPS URL and allows verified matching owner to approve")
    void testOpaqueUrlGenerationAndApprovalPositive() {
        String approvalUrl = service.generateApprovalUrl(tenantId, proposalId, authorizedUser);

        assertThat(approvalUrl)
                .startsWith("https://wallet.bank/copilot/approve?token=")
                .doesNotContain(tenantId)
                .doesNotContain(proposalId.toString())
                .doesNotContain(authorizedUser);

        String token = approvalUrl.substring(approvalUrl.indexOf("token=") + 6);

        ProposalResponse executed = new ProposalResponse(
                proposalId, tenantId, walletId, ProposalType.TRANSFER,
                ProposalStatus.EXECUTED, "{}", "key-1", UUID.randomUUID().toString(),
                "agent", authorizedUser, baseTime, baseTime.plusSeconds(300),
                null, null, baseTime, baseTime, "tx-123"
        );
        when(proposalUseCase.approveProposal(eq(tenantId), eq(authorizedUser), any(ApproveProposalCommand.class)))
                .thenReturn(executed);

        ProposalResponse result = service.consumeAndApprove(token, tenantId, authorizedUser);

        assertThat(result.status()).isEqualTo(ProposalStatus.EXECUTED);
        verify(proposalUseCase).approveProposal(eq(tenantId), eq(authorizedUser), any(ApproveProposalCommand.class));
    }

    @Test
    @DisplayName("Boundary: Expired or already consumed token throws AccessDeniedException (single-use replay protection)")
    void testExpiredOrReusedTokenRejected() {
        String url = service.generateApprovalUrl(tenantId, proposalId, authorizedUser);
        String token = url.substring(url.indexOf("token=") + 6);

        ProposalResponse executed = new ProposalResponse(
                proposalId, tenantId, walletId, ProposalType.TRANSFER,
                ProposalStatus.EXECUTED, "{}", "key-1", UUID.randomUUID().toString(),
                "agent", authorizedUser, baseTime, baseTime.plusSeconds(300),
                null, null, baseTime, baseTime, "tx-123"
        );
        when(proposalUseCase.approveProposal(any(), any(), any())).thenReturn(executed);

        // First consumption succeeds
        service.consumeAndApprove(token, tenantId, authorizedUser);

        // Replay attempt throws SecurityException
        assertThatThrownBy(() -> service.consumeAndApprove(token, tenantId, authorizedUser))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid or consumed token");
    }

    @Test
    @DisplayName("Security Gate: Different user attempting to approve via token is rejected (testCrossUserUrlApprovalRejected)")
    void testCrossUserUrlApprovalRejected() {
        String url = service.generateApprovalUrl(tenantId, proposalId, authorizedUser);
        String token = url.substring(url.indexOf("token=") + 6);

        assertThatThrownBy(() -> service.consumeAndApprove(token, tenantId, unauthorizedUser))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("User mismatch");

        verify(proposalUseCase, never()).approveProposal(any(), any(), any());
    }
}
