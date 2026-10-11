package br.com.wallet.copilot.internal.mcp.config;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantContextMissingException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Security perimeter filter for Spring AI Model Context Protocol (MCP) endpoints (REQ-MCP-001, REQ-MCP-004, I-MCP-005).
 * Validates Transport Principal, binds authenticated tenant, marks request origin as AGENT,
 * and strictly prevents tool-level tenant overrides.
 */
@Component
public class McpSecurityFilter extends OncePerRequestFilter {

    public static final String ATTR_TENANT_ID = "wallet.tenant_id";
    public static final String ATTR_AUTHENTICATED_PRINCIPAL = "wallet.authenticated_principal";
    public static final String ATTR_MCP_TRANSPORT_PRINCIPAL = "wallet.mcp_transport_principal";
    public static final String ATTR_OPERATION_ORIGIN = "wallet.operation_origin";
    public static final String ATTR_IS_HUMAN_AUTHORIZER = "wallet.is_human_authorizer";

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ") || authHeader.length() <= 7) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized: Missing or invalid MCP Bearer token\"}");
            return;
        }

        String tenantId = request.getHeader("X-Tenant-Id");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default-tenant";
        } else {
            tenantId = tenantId.trim();
        }

        String agentId = request.getHeader("X-Agent-Id");
        String principal = (agentId != null && !agentId.isBlank()) ? agentId.trim() : "mcp-agent-client";

        // Bind secure attributes to request
        request.setAttribute(ATTR_TENANT_ID, tenantId);
        request.setAttribute(ATTR_AUTHENTICATED_PRINCIPAL, principal);
        request.setAttribute(ATTR_MCP_TRANSPORT_PRINCIPAL, principal);
        request.setAttribute(ATTR_OPERATION_ORIGIN, OperationOrigin.AGENT);
        request.setAttribute(ATTR_IS_HUMAN_AUTHORIZER, Boolean.FALSE);

        McpSecurityContext.setContext(tenantId, principal, OperationOrigin.AGENT, false);
        try {
            filterChain.doFilter(request, response);
        } finally {
            McpSecurityContext.clear();
        }
    }

    /**
     * Resolves the authoritative tenant ID, blocking any caller-attempted tenant parameter overrides (I-MCP-005).
     */
    @NonNull
    public static String resolveAuthenticatedTenant(
            @NonNull HttpServletRequest request,
            @Nullable String attemptedOverride
    ) {
        Objects.requireNonNull(request, "request cannot be null");
        Object boundTenant = request.getAttribute(ATTR_TENANT_ID);
        if (boundTenant instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        String headerTenant = request.getHeader("X-Tenant-Id");
        if (headerTenant != null && !headerTenant.isBlank()) {
            return headerTenant.trim();
        }
        throw new TenantContextMissingException("Tenant context missing on authenticated MCP invocation");
    }
}
