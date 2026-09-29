package br.com.wallet.unit.edge.security;

import br.com.wallet.core.security.SecurityHeaders;
import br.com.wallet.edge.api.AuthenticatedPrincipal;
import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.api.CredentialMetadata;
import br.com.wallet.edge.api.CredentialSnapshot;
import br.com.wallet.edge.api.ResolvedCredential;
import br.com.wallet.edge.internal.security.HmacAuthenticationFilter;
import br.com.wallet.edge.internal.security.HmacCanonicalizer;
import br.com.wallet.edge.internal.security.HmacSignatureVerifier;
import br.com.wallet.edge.internal.security.InMemoryCredentialResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HmacAuthenticationFilter Tests (I-SEC-001, I-SEC-002, REQ-SEC-001, TASK-SEC-2.6)")
class HmacAuthenticationFilterTest {

    private static final byte[] SECRET = "test-secret-key-32-bytes-long!!!".getBytes(StandardCharsets.UTF_8);
    private static final String KEY_ID = "key-tenant-100";
    private static final String TENANT_ID = "tenant-legit-100";
    private static final String PRINCIPAL_ID = "principal-alpha";

    private final Instant fixedNow = Instant.parse("2026-09-20T20:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private InMemoryCredentialResolver resolver;
    private HmacSignatureVerifier verifier;
    private HmacAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        CredentialMetadata metadata = new CredentialMetadata(
                KEY_ID, TENANT_ID, PRINCIPAL_ID, Set.of("wallet:write"), true
        );
        ResolvedCredential resolved = new ResolvedCredential(metadata, new CredentialMaterial(SECRET));
        CredentialSnapshot snapshot = new CredentialSnapshot(Map.of(KEY_ID, resolved), Map.of());

        resolver = new InMemoryCredentialResolver(snapshot);
        verifier = new HmacSignatureVerifier();
        filter = new HmacAuthenticationFilter(resolver, verifier, clock);
    }

    @Test
    @DisplayName("TASK-SEC-2.6 & I-SEC-001: Must ignore client-supplied X-Tenant-Id and derive tenant strictly from credentials")
    void shouldIgnoreUntrustedTenantHeader() throws ServletException, IOException {
        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        String idempotencyKey = "op-test-123";
        byte[] body = "{\"amount\":100.00}".getBytes(StandardCharsets.UTF_8);

        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "POST", "/operations/transfer", null, KEY_ID, timestamp, idempotencyKey, body
        );
        String signature = verifier.computeSignatureHex(canonical, new CredentialMaterial(SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.X_SIGNATURE, signature);
        request.addHeader(SecurityHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        // Malicious client tries to spoof another tenant:
        request.addHeader("X-Tenant-Id", "tenant-malicious-attacker");
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> {
            filterChainCalled.set(true);
            AuthenticatedPrincipal principal =
                    (AuthenticatedPrincipal) req.getAttribute(HmacAuthenticationFilter.AUTHENTICATED_PRINCIPAL_ATTR);

            assertThat(principal).isNotNull();
            // Tenant must be strictly tenant-legit-100 derived from credentials, NOT tenant-malicious-attacker
            assertThat(principal.tenantId()).isEqualTo(TENANT_ID);
            assertThat(principal.principalId()).isEqualTo(PRINCIPAL_ID);
            assertThat(principal.keyId()).isEqualTo(KEY_ID);
        };

        filter.doFilter(request, response, chain);

        assertThat(filterChainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should reject request when required security headers are missing")
    void shouldRejectWhenMissingHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        // Missing X-Key-Id and X-Signature
        request.addHeader(SecurityHeaders.X_TIMESTAMP, String.valueOf(fixedNow.toEpochMilli()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("MISSING_CREDENTIALS");
    }

    @Test
    @DisplayName("Should reject request when timestamp is skewed beyond 30 seconds (I-SEC-006)")
    void shouldRejectWhenTimestampSkewed() throws ServletException, IOException {
        long skewedTimestamp = fixedNow.minusSeconds(35).toEpochMilli();
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "POST", "/operations/transfer", null, KEY_ID, String.valueOf(skewedTimestamp), "op-1", body
        );
        String signature = verifier.computeSignatureHex(canonical, new CredentialMaterial(SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, String.valueOf(skewedTimestamp));
        request.addHeader(SecurityHeaders.X_SIGNATURE, signature);
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("TIMESTAMP_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("Should reject request when API key is unknown or inactive")
    void shouldRejectWhenKeyUnknown() throws ServletException, IOException {
        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, "unknown-key");
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.X_SIGNATURE, "0".repeat(64));
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("Should reject request when signature is invalid")
    void shouldRejectWhenSignatureInvalid() throws ServletException, IOException {
        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.X_SIGNATURE, "f".repeat(64)); // Invalid signature
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Should bypass non-operations paths (e.g. actuator/health)")
    void shouldBypassNonOperationsEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("TASK-10.11: Should reject duplicate nonce with 401 DUPLICATE_NONCE")
    void shouldRejectDuplicateNonce() throws ServletException, IOException {
        br.com.wallet.security.replay.NonceTracker mockTracker = org.mockito.Mockito.mock(br.com.wallet.security.replay.NonceTracker.class);
        org.mockito.Mockito.when(mockTracker.reserve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new br.com.wallet.security.replay.NonceReservation.Rejected(br.com.wallet.security.replay.ReplayRejectionReason.DUPLICATE_NONCE));

        HmacAuthenticationFilter filterWithNonce = new HmacAuthenticationFilter(resolver, verifier, mockTracker, clock);

        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST", "/operations/transfer", null, KEY_ID, timestamp, "op-1", body
        );
        String signature = verifier.computeSignatureHex(canonicalRequest, new CredentialMaterial(SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.IDEMPOTENCY_KEY, "op-1");
        request.addHeader(SecurityHeaders.X_SIGNATURE, signature);
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filterWithNonce.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("DUPLICATE_NONCE");
    }

    @Test
    @DisplayName("TASK-10.11: Should fail closed with 503 REPLAY_STORAGE_UNAVAILABLE when nonce storage fails")
    void shouldFailClosedWhenNonceStorageUnavailable() throws ServletException, IOException {
        br.com.wallet.security.replay.NonceTracker mockTracker = org.mockito.Mockito.mock(br.com.wallet.security.replay.NonceTracker.class);
        org.mockito.Mockito.when(mockTracker.reserve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new br.com.wallet.security.replay.NonceReservation.Unavailable(br.com.wallet.security.replay.ReplayAvailabilityReason.STORAGE_UNAVAILABLE));

        HmacAuthenticationFilter filterWithNonce = new HmacAuthenticationFilter(resolver, verifier, mockTracker, clock);

        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST", "/operations/transfer", null, KEY_ID, timestamp, "op-1", body
        );
        String signature = verifier.computeSignatureHex(canonicalRequest, new CredentialMaterial(SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.IDEMPOTENCY_KEY, "op-1");
        request.addHeader(SecurityHeaders.X_SIGNATURE, signature);
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filterWithNonce.doFilter(request, response, (req, res) -> filterChainCalled.set(true));

        assertThat(filterChainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("REPLAY_STORAGE_UNAVAILABLE");
    }

    @Test
    @DisplayName("TASK-10.11: Should admit valid nonce and attach ReplayKey to request attribute")
    void shouldAdmitValidNonceAndAttachAttribute() throws ServletException, IOException {
        br.com.wallet.security.replay.NonceTracker mockTracker = org.mockito.Mockito.mock(br.com.wallet.security.replay.NonceTracker.class);
        org.mockito.Mockito.when(mockTracker.reserve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new br.com.wallet.security.replay.NonceReservation.Admitted());

        HmacAuthenticationFilter filterWithNonce = new HmacAuthenticationFilter(resolver, verifier, mockTracker, clock);

        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String canonicalRequest = HmacCanonicalizer.buildCanonicalRequest(
                "POST", "/operations/transfer", null, KEY_ID, timestamp, "op-1", body
        );
        String signature = verifier.computeSignatureHex(canonicalRequest, new CredentialMaterial(SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.IDEMPOTENCY_KEY, "op-1");
        request.addHeader(SecurityHeaders.X_SIGNATURE, signature);
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean(false);

        filterWithNonce.doFilter(request, response, (req, res) -> {
            filterChainCalled.set(true);
            assertThat(req.getAttribute(HmacAuthenticationFilter.REPLAY_KEY_ATTR)).isNotNull();
        });

        assertThat(filterChainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
