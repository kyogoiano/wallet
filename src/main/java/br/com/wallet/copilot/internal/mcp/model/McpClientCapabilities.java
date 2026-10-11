package br.com.wallet.copilot.internal.mcp.model;

import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import org.jspecify.annotations.Nullable;

/**
 * Model representing extracted MCP client capabilities (REQ-MCP-005).
 */
public record McpClientCapabilities(
        boolean supportsFormElicitation,
        boolean supportsUrlElicitation
) {

    public static McpClientCapabilities from(@Nullable final ClientCapabilities capabilities) {
        if (capabilities == null || capabilities.elicitation() == null) {
            return new McpClientCapabilities(false, false);
        }
        boolean form = capabilities.elicitation().form() != null;
        boolean url = capabilities.elicitation().url() != null;
        return new McpClientCapabilities(form, url);
    }
}
