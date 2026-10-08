package br.com.wallet.integration.dlq;

import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.security.envelope.CryptoBytes;
import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EncryptionAlgorithm;
import br.com.wallet.security.envelope.EnvelopeCodec;
import br.com.wallet.security.envelope.EnvelopeVersion;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("DlqOperationsDao Integration Tests (TASK-2.1, REQ-TDLQ-004, I-TDLQ-003, I-TDLQ-004, I-TDLQ-006)")
class DlqOperationsDaoIT extends DockerProperties {

    @Autowired
    private DlqOperationsDao dlqDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private  Clock clock;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("Should persist quarantined event with status QUARANTINED, retry_count=0, next_retry_at=null (REQ-TDLQ-004)")
    void shouldQuarantineWithZeroRetries() {
        UUID id = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent event = new DlqEvent(
                id, operationId, userId, "commands.transfer",
                DlqStatus.PENDING, "Poison message", "{\"error\":\"corrupt\"}",
                0, null, now, null,
                DlqFailureType.POISON, "Transfer", "tenant-alpha"
        );

        dlqDao.recordQuarantined(event);

        Optional<DlqEvent> retrievedOpt = dlqDao.findById(id);
        assertThat(retrievedOpt).isPresent();
        DlqEvent retrieved = retrievedOpt.get();

        assertThat(retrieved.status()).isEqualTo(DlqStatus.QUARANTINED);
        assertThat(retrieved.retryCount()).isEqualTo(0);
        assertThat(retrieved.nextRetryAt()).isNull();
        assertThat(retrieved.failureType()).isEqualTo(DlqFailureType.POISON);
        assertThat(retrieved.tenantId()).isEqualTo("tenant-alpha");

        // Assert claimBatch ignores QUARANTINED records
        List<DlqEvent> claimed = dlqDao.claimBatch(now.plusSeconds(86400), 10);
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("Should retry transient failures with full jitter and cap at 3 retries (I-TDLQ-003)")
    void shouldRetryWithFullJitterAndCapAt3Retries() {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();

        DlqEvent event = new DlqEvent(
                id, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.PENDING, "Lock acquisition failure", "{\"amount\":100}",
                0, null, now, null,
                DlqFailureType.TRANSIENT, "Deposit", "tenant-beta"
        );
        dlqDao.insert(event);

        // Attempt 1: FAILED with retry_count = 1, next_retry_at <= now + 4s (ceiling for r=1)
        dlqDao.markFailed(id, now, DlqFailureType.TRANSIENT);
        DlqEvent attempt1 = dlqDao.findById(id).orElseThrow();
        assertThat(attempt1.status()).isEqualTo(DlqStatus.FAILED);
        assertThat(attempt1.retryCount()).isEqualTo(1);
        assertThat(attempt1.nextRetryAt()).isNotNull();
        assertThat(attempt1.nextRetryAt()).isBetween(now.minusMillis(1), now.plusSeconds(4));

        // Attempt 2: FAILED with retry_count = 2, next_retry_at <= now + 8s (ceiling for r=2)
        now = clock.instant();
        dlqDao.markFailed(id, now, DlqFailureType.TRANSIENT);
        DlqEvent attempt2 = dlqDao.findById(id).orElseThrow();
        assertThat(attempt2.status()).isEqualTo(DlqStatus.FAILED);
        assertThat(attempt2.retryCount()).isEqualTo(2);
        assertThat(attempt2.nextRetryAt()).isNotNull();
        assertThat(attempt2.nextRetryAt()).isBetween(now.minusMillis(1), now.plusSeconds(9));

        // Attempt 3: EXHAUSTED with retry_count = 3, next_retry_at = null
        dlqDao.markFailed(id, now, DlqFailureType.TRANSIENT);
        DlqEvent attempt3 = dlqDao.findById(id).orElseThrow();
        assertThat(attempt3.status()).isEqualTo(DlqStatus.EXHAUSTED);
        assertThat(attempt3.retryCount()).isEqualTo(3);
        assertThat(attempt3.nextRetryAt()).isNull();

        // Automatic claimBatch should NOT claim EXHAUSTED records
        List<DlqEvent> claimed = dlqDao.claimBatch(now.plusSeconds(86400), 10);
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("Should filter DLQ operations by tenant_id (REQ-TDLQ-008)")
    void shouldFilterByTenantId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent event1 = new DlqEvent(
                id1, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.QUARANTINED, "Poison", "{}", 0, null, now, null,
                DlqFailureType.POISON, "Deposit", "tenant-1"
        );
        DlqEvent event2 = new DlqEvent(
                id2, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.QUARANTINED, "Poison", "{}", 0, null, now, null,
                DlqFailureType.POISON, "Deposit", "tenant-2"
        );
        dlqDao.insert(event1);
        dlqDao.insert(event2);

        List<DlqEvent> filtered = dlqDao.findByFilter(new DlqQueryFilter(null, null, null, null, "tenant-1"), 10, 0);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.getFirst().id()).isEqualTo(id1);
        assertThat(filtered.getFirst().tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("Should persist opaque CryptoEnvelope in dlq_operations table and verify zero plaintext in PostgreSQL (I-TDLQ-006, REQ-TDLQ-007)")
    void shouldAssertZeroPlaintextInDatabase() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID opId = UUID.randomUUID();
        final String sensitivePlaintext = "{\"amount\": 15000.50, \"fromAccount\": \"acc-123\", \"toAccount\": \"acc-456\"}";

        // Real AES-GCM encryption of sensitive financial command
        final byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        final SecretKey key = new SecretKeySpec(new byte[32], "AES");
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        final byte[] ciphertext = cipher.doFinal(sensitivePlaintext.getBytes(StandardCharsets.UTF_8));

        final CryptoEnvelope envelope = new CryptoEnvelope(
                EnvelopeVersion.WALLET_ENV_V1,
                new TenantId("tenant-secure"),
                new OperationId(opId),
                new KeyId("key-2026-q1"),
                EncryptionAlgorithm.AES_256_GCM,
                new CryptoBytes(iv),
                new CryptoBytes(new byte[32]), // wrapped DEK
                new CryptoBytes(ciphertext)
        );

        final byte[] binaryEnvelope = EnvelopeCodec.encode(envelope);
        final String base64Payload = Base64.getEncoder().encodeToString(binaryEnvelope);

        final DlqEvent event = new DlqEvent(
                id, opId, UUID.randomUUID(), "commands.transfer",
                DlqStatus.FAILED, "Transient deadlock", base64Payload,
                0, null, Instant.now(), null,
                DlqFailureType.TRANSIENT, "Transfer", "tenant-secure"
        );

        dlqDao.insert(event);

        // 1. Direct raw SQL assertion against PostgreSQL checking that NO plaintext leaked into the database
        final Integer leakCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM dlq_operations WHERE id = ? AND (" +
                "  payload::text ILIKE '%15000.50%' " +
                "  OR payload::text ILIKE '%acc-123%' " +
                "  OR payload::text ILIKE '%acc-456%' " +
                "  OR payload::text ILIKE '%fromAccount%' " +
                "  OR payload::text ILIKE '%toAccount%')",
                Integer.class,
                id
        );
        assertThat(leakCount).isZero();

        // 2. Retrieve raw column value from PostgreSQL and assert it is exact opaque base64
        String rawDbPayload = jdbcTemplate.queryForObject(
                "SELECT payload #>> '{}' FROM dlq_operations WHERE id = ?",
                String.class,
                id
        );
        if (rawDbPayload != null && rawDbPayload.startsWith("\"") && rawDbPayload.endsWith("\"")) {
            rawDbPayload = rawDbPayload.substring(1, rawDbPayload.length() - 1);
        }
        assertThat(rawDbPayload).isEqualTo(base64Payload);

        // 3. Assert raw payload decodes to the exact valid CryptoEnvelope
        final byte[] decodedDbBytes = Base64.getDecoder().decode(rawDbPayload);
        final CryptoEnvelope restored = EnvelopeCodec.decode(decodedDbBytes);
        assertThat(restored.operationId().value()).isEqualTo(opId);
        assertThat(restored.tenantId().value()).isEqualTo("tenant-secure");
        assertThat(restored.ciphertext().value()).isEqualTo(ciphertext);
    }
}
