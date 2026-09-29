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
    public static final String REPLAY_KEY_ATTR = "wallet.replay_key";
    public static final long MAX_TIMESTAMP_SKEW_MILLIS = 30_000L;

    private final CredentialResolver credentialResolver;
    private final HmacSignatureVerifier signatureVerifier;
    private final br.com.wallet.security.replay.NonceTracker nonceTracker;
    private final Clock clock;

    public HmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier
    ) {
        this(credentialResolver, signatureVerifier, null, Clock.systemUTC());
    }

    public HmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier,
            Clock clock
    ) {
        this(credentialResolver, signatureVerifier, null, clock);
    }

    public HmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier,
            br.com.wallet.security.replay.NonceTracker nonceTracker
    ) {
        this(credentialResolver, signatureVerifier, nonceTracker, Clock.systemUTC());
    }

    @Autowired
    public HmacAuthenticationFilter(
            CredentialResolver credentialResolver,
            HmacSignatureVerifier signatureVerifier,
            @Autowired(required = false) br.com.wallet.security.replay.NonceTracker nonceTracker,
            @Autowired(required = false) Clock clock
    ) {
        this.credentialResolver = credentialResolver;
        this.signatureVerifier = signatureVerifier;
        this.nonceTracker = nonceTracker;
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

        if (nonceTracker != null) {
            String nonce = request.getHeader(SecurityHeaders.X_NONCE);
            if (isBlank(nonce)) {
                nonce = idempotencyKey;
            }
            if (isBlank(nonce)) {
                nonce = timestampStr;
            }

            br.com.wallet.security.replay.ReplayKey replayKey = new br.com.wallet.security.replay.ReplayKey(
                    new br.com.wallet.security.envelope.TenantId(principal.tenantId()),
                    new br.com.wallet.security.replay.PrincipalId(principal.principalId()),
                    nonce
            );

            br.com.wallet.security.replay.NonceReservation reservation = nonceTracker.reserve(replayKey);
            if (reservation instanceof br.com.wallet.security.replay.NonceReservation.Rejected rejected) {
                log.warn("Nonce reservation rejected for key {}: {}", replayKey.toStorageKey(), rejected.reason());
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "DUPLICATE_NONCE",
                        "Nonce replay detected: " + rejected.reason());
                return;
            } else if (reservation instanceof br.com.wallet.security.replay.NonceReservation.Unavailable unavailable) {
                log.error("Nonce reservation unavailable for key {}: {}", replayKey.toStorageKey(), unavailable.reason());
                sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "REPLAY_STORAGE_UNAVAILABLE",
                        "Replay protection storage is temporarily unavailable: " + unavailable.reason());
                return;
            } else if (reservation instanceof br.com.wallet.security.replay.NonceReservation.Admitted) {
                cachedRequest.setAttribute(REPLAY_KEY_ATTR, replayKey);
            }
        }

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
        String errorName = status == HttpServletResponse.SC_SERVICE_UNAVAILABLE ? "SERVICE_UNAVAILABLE" : "UNAUTHORIZED";
        response.getWriter().write(String.format(
                "{\"error\":\"%s\",\"code\":\"%s\",\"message\":\"%s\"}",
                errorName, code, message
        ));
    }
}
