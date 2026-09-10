package br.com.wallet.edge.internal.transport;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter advertising HTTP/3 over QUIC capability to web and mobile clients (REQ-EDG-020).
 * Injects the standard Alt-Svc response header:
 * Alt-Svc: h3=":8443"; ma=86400; persist=1
 */
@Component
public class AltSvcWebFilter extends OncePerRequestFilter {

    public static final String ALT_SVC_HEADER = "Alt-Svc";
    public static final String DEFAULT_ALT_SVC_VALUE = "h3=\":8443\"; ma=86400; persist=1";
    public static final String EARLY_DATA_HEADER = "Early-Data";
    public static final int HTTP_TOO_EARLY = 425;

    private final String altSvcValue;

    public AltSvcWebFilter() {
        this(DEFAULT_ALT_SVC_VALUE);
    }

    public AltSvcWebFilter(String altSvcValue) {
        this.altSvcValue = altSvcValue;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader(ALT_SVC_HEADER, altSvcValue);

        // REQ-EDG-020: Financial mutations strictly require 1-RTT handshake to eliminate 0-RTT early-data replay
        if (isEarlyData(request) && isFinancialMutation(request)) {
            response.setStatus(HTTP_TOO_EARLY);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"TOO_EARLY\",\"message\":\"Financial mutations require 1-RTT handshake to prevent 0-RTT replay\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isEarlyData(HttpServletRequest request) {
        String earlyData = request.getHeader(EARLY_DATA_HEADER);
        return "1".equals(earlyData) || "true".equalsIgnoreCase(earlyData);
    }

    private boolean isFinancialMutation(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/operations");
    }
}
