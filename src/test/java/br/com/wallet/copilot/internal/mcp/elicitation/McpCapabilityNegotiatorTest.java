package br.com.wallet.copilot.internal.mcp.elicitation;

import br.com.wallet.copilot.internal.mcp.elicitation.McpCapabilityNegotiator.InteractionTier;
import br.com.wallet.copilot.internal.mcp.model.McpClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpCapabilityNegotiatorTest {

    private McpCapabilityNegotiator negotiator;

    @BeforeEach
    void setUp() {
        negotiator = new McpCapabilityNegotiator();
    }

    @Test
    @DisplayName("Positive: Client with Form Elicitation capability selects FORM tier")
    void testFormElicitationSelected() {
        McpSchema.ClientCapabilities schemaCaps = McpSchema.ClientCapabilities.builder()
                .elicitation(McpSchema.ClientCapabilities.Elicitation.builder()
                        .form(new McpSchema.ClientCapabilities.Elicitation.Form())
                        .build())
                .build();

        McpClientCapabilities caps = McpClientCapabilities.from(schemaCaps);
        InteractionTier tier = negotiator.negotiate(caps);

        assertThat(tier).isEqualTo(InteractionTier.FORM);
    }

    @Test
    @DisplayName("Positive: Client with URL Elicitation capability only selects URL tier")
    void testUrlElicitationSelected() {
        McpSchema.ClientCapabilities schemaCaps = McpSchema.ClientCapabilities.builder()
                .elicitation(McpSchema.ClientCapabilities.Elicitation.builder()
                        .url(new McpSchema.ClientCapabilities.Elicitation.Url())
                        .build())
                .build();

        McpClientCapabilities caps = McpClientCapabilities.from(schemaCaps);
        InteractionTier tier = negotiator.negotiate(caps);

        assertThat(tier).isEqualTo(InteractionTier.URL);
    }

    @Test
    @DisplayName("Boundary: Client with null or empty capabilities degrades to FALLBACK_EXTERNAL tier")
    void testNullOrEmptyDegradesToFallback() {
        assertThat(negotiator.negotiate(null)).isEqualTo(InteractionTier.FALLBACK_EXTERNAL);

        McpClientCapabilities emptyCaps = new McpClientCapabilities(false, false);
        assertThat(negotiator.negotiate(emptyCaps)).isEqualTo(InteractionTier.FALLBACK_EXTERNAL);
    }

    @Test
    @DisplayName("Invariant Gate: Unsupported client returns FALLBACK_EXTERNAL and NEVER triggers auto-execution (I-MCP-002)")
    void testUnsupportedDegradesToExternal() {
        McpSchema.ClientCapabilities schemaCaps = McpSchema.ClientCapabilities.builder().build();
        McpClientCapabilities caps = McpClientCapabilities.from(schemaCaps);

        InteractionTier tier = negotiator.negotiate(caps);

        assertThat(tier).isEqualTo(InteractionTier.FALLBACK_EXTERNAL);
        assertThat(negotiator.buildFallbackInstructions("prop-uuid-1234"))
                .contains("https://wallet.bank/copilot/proposals/prop-uuid-1234/review")
                .contains("Human authorization required");
    }
}
