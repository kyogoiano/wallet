package br.com.wallet.edge.internal.security;

import br.com.wallet.core.security.SecurityHeaders;
import br.com.wallet.edge.api.AuthenticatedPrincipal;
import br.com.wallet.edge.api.CredentialResolver;
import br.com.wallet.edge.api.ResolvedCredential;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Optional;

/**
 * Perimeter authentication filter verifying WALLET-HMAC-V1 request signatures (I-SEC-001, I-SEC-002, REQ-SEC-001).
 * <p>
 * Completely ignores any client-supplied tenant headers; the authenticated principal's tenant
 * is derived strictly from verified cryptographic credential metadata (I-SEC-001).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HmacAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HmacAuthenticationFilter.class);

    public static final String AUTHENTICATED_PRINCIPAL_ATTR = "wallet.authenticated_principal";
    public static final long MAX_TIMESTAMP_SKEW_MILLIS = 30_000L;

    private final CredentialResolver credentialResolver;
    private final HmacSignatureVerifier signatureVerifier;
    private final Clock clock;

    public HmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier
    ) {
        this(credentialResolver, signatureVerifier, Clock.systemUTC());
    }

    @Autowired
    public HmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier,
            @Autowired(required = false) Clock clock
    ) {
        this.credentialResolver = credentialResolver;
        this.signatureVerifier = signatureVerifier;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Only enforce perimeter HMAC on operations endpoints (command admission and streaming)
        return !uri.startsWith("/operations");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String keyId = request.getHeader(SecurityHeaders.X_KEY_ID);
        String timestampStr = request.getHeader(SecurityHeaders.X_TIMESTAMP);
        String signature = request.getHeader(SecurityHeaders.X_SIGNATURE);

        if (isBlank(keyId) || isBlank(timestampStr) || isBlank(signature)) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "MISSING_CREDENTIALS",
                    "Missing required HMAC security headers (X-Key-Id, X-Timestamp, X-Signature)");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr.trim());
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "TIMESTAMP_OUT_OF_RANGE",
                    "Invalid timestamp format; must be epoch milliseconds");
            return;
        }

        long now = clock.millis();
        if (Math.abs(now - timestamp) > MAX_TIMESTAMP_SKEW_MILLIS) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "TIMESTAMP_OUT_OF_RANGE",
                    "Request timestamp is skewed beyond 30000ms window");
            return;
        }

        Optional<ResolvedCredential> resolvedOpt = credentialResolver.resolve(keyId);
        if (resolvedOpt.isEmpty()) {
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "Invalid or inactive API key");
            return;
        }

        ResolvedCredential resolved = resolvedOpt.get();

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        byte[] body = cachedRequest.getBody();

        String idempotencyKey = request.getHeader(SecurityHeaders.IDEMPOTENCY_KEY);

        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                keyId,
                timestampStr,
                idempotencyKey,
                body
        );

        if (!signatureVerifier.verify(canonicalRequest, signature, resolved.material())) {
            log.warn("HMAC verification failed for keyId: {}, uri: {}", keyId, request.getRequestURI());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_SIGNATURE",
                    "HMAC signature verification failed");
            return;
        }

        // Derive tenant strictly from verified credential metadata (I-SEC-001)
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                resolved.metadata().principalId(),
                resolved.metadata().tenantId(),
                resolved.metadata().keyId(),
                resolved.metadata().permissions()
        );

        cachedRequest.setAttribute(AUTHENTICATED_PRINCIPAL_ATTR, principal);
        filterChain.doFilter(cachedRequest, response);
    }

    private boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\":\"UNAUTHORIZED\",\"code\":\"%s\",\"message\":\"%s\"}",
                code, message
        ));
    }
}
