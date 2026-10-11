package br.com.wallet.copilot.internal.mcp.elicitation;

import br.com.wallet.copilot.internal.mcp.model.McpClientCapabilities;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Three-tier capability negotiation engine for Model Context Protocol (MCP) clients (REQ-MCP-005, I-MCP-002).
 * Determines interaction mode with strict deterministic fallback when elicitation is unavailable.
 */
@Component
public class McpCapabilityNegotiator {

    public enum InteractionTier {
        FORM,
        URL,
        FALLBACK_EXTERNAL
    }

    @NonNull
    public InteractionTier negotiate(@Nullable final McpClientCapabilities capabilities) {
        if (capabilities == null) {
            return InteractionTier.FALLBACK_EXTERNAL;
        }
        if (capabilities.supportsFormElicitation()) {
            return InteractionTier.FORM;
        }
        if (capabilities.supportsUrlElicitation()) {
            return InteractionTier.URL;
        }
        return InteractionTier.FALLBACK_EXTERNAL;
    }

    @NonNull
    public String buildFallbackInstructions(@NonNull final String proposalId) {
        Objects.requireNonNull(proposalId, "proposalId cannot be null");
        return "Proposal created in PROPOSED status. Human authorization required. " +
                "Please review and authorize via Web/CLI: https://wallet.bank/copilot/proposals/" +
                proposalId + "/review";
    }
}
