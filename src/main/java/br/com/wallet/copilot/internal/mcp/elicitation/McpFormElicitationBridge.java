package br.com.wallet.copilot.internal.mcp.elicitation;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Bridge between MCP Form Elicitation interactions and domain financial authorization (REQ-MCP-006, I-MCP-002).
 * Enforces Three-Identity Non-Conflation: Agent `accept` cannot authorize without verified human principal (I-MCP-007).
 * Distinguishes explicit `decline` from non-committal `cancel` (final.txt 1.4).
 */
@Component
public class McpFormElicitationBridge {

    public enum ElicitationAction {
        ACCEPT,
        DECLINE,
        CANCEL
    }

    public record FormElicitationResult(
            @NonNull ProposalStatus status,
            @NonNull String message
    ) {}

    private final ProposalUseCase proposalUseCase;

    public McpFormElicitationBridge(@NonNull final ProposalUseCase proposalUseCase) {
        this.proposalUseCase = Objects.requireNonNull(proposalUseCase, "proposalUseCase cannot be null");
    }

    @NonNull
    public FormElicitationResult handleElicitationResponse(
            @NonNull final UUID proposalId,
            @NonNull final ElicitationAction action,
            @Nullable final String reason
    ) {
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        String tenantId = McpSecurityContext.getTenantId();
        String principal = McpSecurityContext.getPrincipal() != null ? McpSecurityContext.getPrincipal() : "mcp-agent";

        return switch (action) {
            case ACCEPT -> handleAccept(tenantId, principal, proposalId);
            case DECLINE -> handleDecline(tenantId, principal, proposalId, reason);
            case CANCEL -> handleCancel(proposalId);
        };
    }

    private FormElicitationResult handleAccept(String tenantId, String principal, UUID proposalId) {
        // Enforce Three-Identity Non-Conflation Gate (I-MCP-007, testAgentCannotApprove)
        if (!McpSecurityContext.isHumanAuthorizer()) {
            return new FormElicitationResult(
                    ProposalStatus.PROPOSED,
                    "Human authorization required. Agent confirmation payload is not an authorization credential. " +
                            "Please review and authorize via Web/CLI: https://wallet.bank/copilot/proposals/" +
                            proposalId + "/review"
            );
        }

        ProposalResponse response = proposalUseCase.approveProposal(
                tenantId,
                principal,
                new ApproveProposalCommand(proposalId)
        );
        return new FormElicitationResult(response.status(), "Proposal " + proposalId + " authorized successfully.");
    }

    private FormElicitationResult handleDecline(String tenantId, String principal, UUID proposalId, String reason) {
        ProposalResponse response = proposalUseCase.rejectProposal(
                tenantId,
                principal,
                new RejectProposalCommand(proposalId, reason)
        );
        return new FormElicitationResult(response.status(), "Proposal " + proposalId + " rejected: " + reason);
    }

    private FormElicitationResult handleCancel(UUID proposalId) {
        // Cancel interaction closes the dialog without mutating financial state (final.txt 1.4)
        return new FormElicitationResult(
                ProposalStatus.PROPOSED,
                "Interaction cancelled. Proposal remains pending in PROPOSED status."
        );
    }
}
