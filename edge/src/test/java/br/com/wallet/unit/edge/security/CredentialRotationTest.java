package br.com.wallet.unit.edge.security;

import br.com.wallet.core.security.SecurityHeaders;
import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.api.CredentialMetadata;
import br.com.wallet.edge.api.CredentialSnapshot;
import br.com.wallet.edge.api.ResolvedCredential;
import br.com.wallet.edge.internal.security.HmacAuthenticationFilter;
import br.com.wallet.edge.internal.security.HmacCanonicalizer;
import br.com.wallet.edge.internal.security.HmacSignatureVerifier;
import br.com.wallet.edge.internal.security.InMemoryCredentialResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Zero-Downtime Credential Rotation Tests (REQ-SEC-010, TASK-SEC-2.5)")
class CredentialRotationTest {

    private static final byte[] OLD_SECRET = "secret-v1-old-key-32-bytes-long!".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW_SECRET = "secret-v2-new-key-32-bytes-long!".getBytes(StandardCharsets.UTF_8);

    private static final String OLD_KEY_ID = "key-tenant-v1";
    private static final String NEW_KEY_ID = "key-tenant-v2";
    private static final String RETIRED_KEY_ID = "key-tenant-v0";
    private static final String TENANT_ID = "tenant-rotation-corp";
    private static final String PRINCIPAL_ID = "principal-rotation";

    private final Instant fixedNow = Instant.parse("2026-09-20T20:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private InMemoryCredentialResolver resolver;
    private HmacSignatureVerifier verifier;
    private HmacAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        CredentialMetadata metaOld = new CredentialMetadata(
                OLD_KEY_ID, TENANT_ID, PRINCIPAL_ID, Set.of("wallet:write"), true
        );
        CredentialMetadata metaNew = new CredentialMetadata(
                NEW_KEY_ID, TENANT_ID, PRINCIPAL_ID, Set.of("wallet:write"), true
        );

        ResolvedCredential credOld = new ResolvedCredential(metaOld, new CredentialMaterial(OLD_SECRET));
        ResolvedCredential credNew = new ResolvedCredential(metaNew, new CredentialMaterial(NEW_SECRET));

        // Overlapping configuration: NEW_KEY_ID is active, OLD_KEY_ID is in retiring grace period
        CredentialSnapshot rotationSnapshot = new CredentialSnapshot(
                Map.of(NEW_KEY_ID, credNew),
                Map.of(OLD_KEY_ID, credOld)
        );

        resolver = new InMemoryCredentialResolver(rotationSnapshot);
        verifier = new HmacSignatureVerifier();
        filter = new HmacAuthenticationFilter(resolver, verifier, clock);
    }

    @Test
    @DisplayName("REQ-SEC-010: Request signed with newly activated key (v2) must be accepted")
    void shouldAcceptRequestWithNewActiveKey() throws Exception {
        assertRequestAcceptedWithKey(NEW_KEY_ID, NEW_SECRET);
    }

    @Test
    @DisplayName("REQ-SEC-010: Request signed with retiring key (v1) during grace period must be accepted")
    void shouldAcceptRequestWithRetiringKeyDuringGracePeriod() throws Exception {
        assertRequestAcceptedWithKey(OLD_KEY_ID, OLD_SECRET);
        assertThat(resolver.isRetiring(OLD_KEY_ID)).isTrue();
    }

    @Test
    @DisplayName("REQ-SEC-010: Request signed with fully retired key (v0) must be rejected with 401")
    void shouldRejectRequestWithFullyRetiredKey() throws Exception {
        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, RETIRED_KEY_ID);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.X_SIGNATURE, "a".repeat(64));
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_CREDENTIALS");
    }

    private void assertRequestAcceptedWithKey(String keyId, byte[] secret) throws Exception {
        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        String opId = "op-" + keyId;
        byte[] body = "{\"amount\":10.00}".getBytes(StandardCharsets.UTF_8);

        String canonical = HmacCanonicalizer.buildCanonicalRequest(
                "POST", "/operations/transfer", null, keyId, timestamp, opId, body
        );
        String signature = verifier.computeSignatureHex(canonical, new CredentialMaterial(secret));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(SecurityHeaders.X_KEY_ID, keyId);
        request.addHeader(SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(SecurityHeaders.X_SIGNATURE, signature);
        request.addHeader(SecurityHeaders.IDEMPOTENCY_KEY, opId);
        request.setContent(body);

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
