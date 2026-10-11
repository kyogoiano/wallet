package br.com.wallet.copilot.internal.mcp.config;

import br.com.wallet.core.context.OperationOrigin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpSecurityFilterTest {

    private McpSecurityFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new McpSecurityFilter();
    }

    @Test
    @DisplayName("Positive: Authenticated Bearer request populates SecurityContext and continues chain")
    void testAuthenticatedBearerRequestContinuesChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("Authorization", "Bearer valid-mcp-agent-token");
        request.addHeader("X-Tenant-Id", "tenant-alpha");
        request.addHeader("X-Agent-Id", "agent-orchestrator-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(request.getAttribute("wallet.tenant_id")).isEqualTo("tenant-alpha");
        assertThat(request.getAttribute("wallet.mcp_transport_principal")).isEqualTo("agent-orchestrator-1");
        assertThat(request.getAttribute("wallet.operation_origin")).isEqualTo(OperationOrigin.AGENT);
        assertThat(request.getAttribute("wallet.is_human_authorizer")).isEqualTo(false);
    }

    @Test
    @DisplayName("Boundary: Unauthenticated request to /mcp returns HTTP 401 Unauthorized")
    void testUnauthenticatedRequestReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    @DisplayName("Security Gate: Tool parameter tenant override blocked and overridden by authenticated tenant context (I-MCP-005)")
    void testTenantOverrideBlocked() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("Authorization", "Bearer valid-mcp-agent-token");
        request.addHeader("X-Tenant-Id", "tenant-alpha");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(McpSecurityFilter.resolveAuthenticatedTenant(request, "tenant-attacker-override"))
                .isEqualTo("tenant-alpha");
    }
}
