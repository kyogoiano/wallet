package br.com.wallet.edge.internal.security;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.api.CredentialMaterial;
import br.com.wallet.edge.api.CredentialMetadata;
import br.com.wallet.edge.api.CredentialSnapshot;
import br.com.wallet.edge.api.ResolvedCredential;
import br.com.wallet.edge.internal.command.CommandAcceptanceService;
import br.com.wallet.edge.internal.security.replay.DragonflyNonceTracker;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.replay.NonceReservation;
import br.com.wallet.security.replay.PrincipalId;
import br.com.wallet.security.replay.ReplayKey;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("TASK-10.15: Two-Phase Nonce Admission Failure Recovery Suite (I-ENV-004)")
class NonceAdmissionFailureRecoveryTest {

    private static final byte[] SECRET = "super-secret-key-32-bytes-long!".getBytes(StandardCharsets.UTF_8);
    private static final String KEY_ID = "key-alpha";
    private static final String TENANT_ID = "tenant-alpha";
    private static final String PRINCIPAL_ID = "principal-alpha";

    private final Instant fixedNow = Instant.parse("2026-09-20T20:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    @Mock
    private RedisCommands<String, String> redisCommands;

    private final Map<String, String> redisStore = new ConcurrentHashMap<>();
    private DragonflyNonceTracker nonceTracker;
    private InMemoryCredentialResolver resolver;
    private HmacSignatureVerifier verifier;
    private HmacAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        // Wire in-memory behavior to mock RedisCommands
        when(redisCommands.set(any(), any(), any(SetArgs.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);

            // Simulate NX semantics
            if (redisStore.containsKey(key) && "RESERVED".equals(value)) {
                return null;
            }
            redisStore.put(key, value);
            return "OK";
        });

        when(redisCommands.del(any())).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            long count = 0;
            if (arg instanceof String[] keys) {
                for (String k : keys) {
                    if (redisStore.remove(k) != null) count++;
                }
            } else if (arg instanceof String singleKey) {
                if (redisStore.remove(singleKey) != null) count++;
            }
            return count;
        });

        nonceTracker = new DragonflyNonceTracker(redisCommands);

        CredentialMetadata metadata = new CredentialMetadata(KEY_ID, TENANT_ID, PRINCIPAL_ID, Set.of("wallet:write"), true);
        ResolvedCredential resolved = new ResolvedCredential(metadata, new CredentialMaterial(SECRET));
        CredentialSnapshot snapshot = new CredentialSnapshot(Map.of(KEY_ID, resolved), Map.of());

        resolver = new InMemoryCredentialResolver(snapshot);
        verifier = new HmacSignatureVerifier();
        filter = new HmacAuthenticationFilter(resolver, verifier, nonceTracker, clock);
    }

    private MockHttpServletRequest buildRequest(String nonce) {
        String timestamp = String.valueOf(fixedNow.toEpochMilli());
        byte[] body = "{\"amount\":100.00}".getBytes(StandardCharsets.UTF_8);
        String canonical = HmacCanonicalizer.buildCanonicalRequest("POST", "/operations/transfer", null, KEY_ID, timestamp, nonce, body);
        String signature = verifier.computeSignatureHex(canonical, new CredentialMaterial(SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/operations/transfer");
        request.addHeader(br.com.wallet.core.security.SecurityHeaders.X_KEY_ID, KEY_ID);
        request.addHeader(br.com.wallet.core.security.SecurityHeaders.X_TIMESTAMP, timestamp);
        request.addHeader(br.com.wallet.core.security.SecurityHeaders.IDEMPOTENCY_KEY, nonce);
        request.addHeader(br.com.wallet.core.security.SecurityHeaders.X_SIGNATURE, signature);
        request.setContent(body);
        return request;
    }

    @Test
    @DisplayName("Scenario 1: Nonce reserved -> KMS failure before journal -> release nonce -> retry succeeds (I-ENV-004)")
    void shouldRecoverWhenKmsFailsPreJournal() throws Exception {
        String nonce = "nonce-transient-kms-fail";
        MockHttpServletRequest request1 = buildRequest(nonce);
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        filter.doFilter(request1, response1, (req, res) -> {
            ReplayKey replayKey = (ReplayKey) req.getAttribute(HmacAuthenticationFilter.REPLAY_KEY_ATTR);
            assertThat(replayKey).isNotNull();

            // Simulate KMS failure during envelope encryption: release the lease
            nonceTracker.release(replayKey);
        });

        // Client retries immediately with identical nonce
        MockHttpServletRequest retryRequest = buildRequest(nonce);
        MockHttpServletResponse retryResponse = new MockHttpServletResponse();
        AtomicBoolean retryAdmitted = new AtomicBoolean(false);

        filter.doFilter(retryRequest, retryResponse, (req, res) -> retryAdmitted.set(true));

        assertThat(retryAdmitted).isTrue();
        assertThat(retryResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Scenario 2: Nonce reserved -> journal write failure -> release nonce -> retry succeeds (I-ENV-004)")
    void shouldRecoverWhenJournalFails() throws Exception {
        String nonce = "nonce-transient-disk-fail";
        MockHttpServletRequest request1 = buildRequest(nonce);
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        filter.doFilter(request1, response1, (req, res) -> {
            ReplayKey replayKey = (ReplayKey) req.getAttribute(HmacAuthenticationFilter.REPLAY_KEY_ATTR);
            assertThat(replayKey).isNotNull();

            // Simulate disk full or IO exception during journal flush: release the lease
            nonceTracker.release(replayKey);
        });

        // Client retries with same nonce
        MockHttpServletRequest retryRequest = buildRequest(nonce);
        MockHttpServletResponse retryResponse = new MockHttpServletResponse();
        AtomicBoolean retryAdmitted = new AtomicBoolean(false);

        filter.doFilter(retryRequest, retryResponse, (req, res) -> retryAdmitted.set(true));

        assertThat(retryAdmitted).isTrue();
        assertThat(retryResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Scenario 3: Nonce reserved -> successful journal fsync -> committed -> duplicate retry rejected with 401")
    void shouldRejectDuplicateWhenCommitted() throws Exception {
        String nonce = "nonce-committed-success";
        MockHttpServletRequest request1 = buildRequest(nonce);
        MockHttpServletResponse response1 = new MockHttpServletResponse();

        filter.doFilter(request1, response1, (req, res) -> {
            ReplayKey replayKey = (ReplayKey) req.getAttribute(HmacAuthenticationFilter.REPLAY_KEY_ATTR);
            assertThat(replayKey).isNotNull();

            // Journal fsync completed: commit the nonce
            nonceTracker.commit(replayKey);
        });

        // Attacker or duplicate request replays same nonce
        MockHttpServletRequest replayRequest = buildRequest(nonce);
        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        AtomicBoolean replayAdmitted = new AtomicBoolean(false);

        filter.doFilter(replayRequest, replayResponse, (req, res) -> replayAdmitted.set(true));

        assertThat(replayAdmitted).isFalse();
        assertThat(replayResponse.getStatus()).isEqualTo(401);
        assertThat(replayResponse.getContentAsString()).contains("DUPLICATE_NONCE");
    }
}
