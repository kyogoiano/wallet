package br.com.wallet.copilot.internal.mcp.config;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantContextMissingException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Thread-local security context holder for Model Context Protocol (MCP) invocations (REQ-MCP-004, I-MCP-005).
 * Explicitly separates Transport Principal, Agent Client, and Human Authorizer (I-MCP-007).
 */
public final class McpSecurityContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_PRINCIPAL = new ThreadLocal<>();
    private static final ThreadLocal<OperationOrigin> CURRENT_ORIGIN = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_HUMAN_AUTHORIZER = new ThreadLocal<>();

    private McpSecurityContext() {}

    public static void setContext(@NonNull String tenantId, @NonNull String principal) {
        setContext(tenantId, principal, OperationOrigin.AGENT, false);
    }

    public static void setContext(
            @NonNull String tenantId,
            @NonNull String principal,
            @NonNull OperationOrigin origin,
            boolean isHumanAuthorizer
    ) {
        CURRENT_TENANT.set(Objects.requireNonNull(tenantId, "tenantId cannot be null"));
        CURRENT_PRINCIPAL.set(Objects.requireNonNull(principal, "principal cannot be null"));
        CURRENT_ORIGIN.set(Objects.requireNonNull(origin, "origin cannot be null"));
        IS_HUMAN_AUTHORIZER.set(isHumanAuthorizer);
    }

    @NonNull
    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantContextMissingException("Authenticated tenant context is missing");
        }
        return tenantId;
    }

    @Nullable
    public static String getPrincipal() {
        return CURRENT_PRINCIPAL.get();
    }

    @NonNull
    public static OperationOrigin getOrigin() {
        OperationOrigin origin = CURRENT_ORIGIN.get();
        return origin != null ? origin : OperationOrigin.AGENT;
    }

    public static boolean isHumanAuthorizer() {
        Boolean human = IS_HUMAN_AUTHORIZER.get();
        return Boolean.TRUE.equals(human);
    }

    public static void setHumanAuthorizer(boolean human) {
        IS_HUMAN_AUTHORIZER.set(human);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_PRINCIPAL.remove();
        CURRENT_ORIGIN.remove();
        IS_HUMAN_AUTHORIZER.remove();
    }
}
