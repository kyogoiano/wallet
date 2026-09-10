package br.com.wallet.edge.internal.ingress;

import br.com.wallet.edge.api.OperationAuthorizationProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter enforcing tenant authorization before establishing SSE push streams (TASK-5.11, REQ-EDG-019).
 * Rejects requests with HTTP 401 Unauthorized if missing tenant credentials,
 * and HTTP 403 Forbidden if the tenant does not own the requested operation.
 */
@Component
public class OperationStatusAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OperationStatusAuthorizationFilter.class);
    public static final String TENANT_HEADER = "X-Tenant-Id";
    private static final Pattern STREAM_PATH_PATTERN =
            Pattern.compile("^/operations/([0-9a-fA-F\\-]+)/stream$");

    private final OperationAuthorizationProvider authorizationProvider;

    public OperationStatusAuthorizationFilter() {
        this((operationId, tenantId) -> true);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OperationStatusAuthorizationFilter(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            OperationAuthorizationProvider authorizationProvider
    ) {
        this.authorizationProvider = authorizationProvider != null
                ? authorizationProvider
                : (operationId, tenantId) -> true;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String uri = request.getRequestURI();
        Matcher matcher = STREAM_PATH_PATTERN.matcher(uri);

        if (matcher.matches()) {
            UUID operationId;
            try {
                operationId = UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"BAD_REQUEST\",\"message\":\"Invalid operationId UUID format\"}");
                return;
            }

            String tenantId = request.getHeader(TENANT_HEADER);
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("Unauthorized SSE stream attempt for opId {}: missing '{}' header", operationId, TENANT_HEADER);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing required authentication header 'X-Tenant-Id'\"}");
                return;
            }

            if (!authorizationProvider.isAuthorized(operationId, tenantId)) {
                log.warn("Forbidden SSE stream attempt for opId {} by tenant {}", operationId, tenantId);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied: operation does not belong to the authorized tenant\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
