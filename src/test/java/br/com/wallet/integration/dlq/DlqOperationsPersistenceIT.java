package br.com.wallet.integration.dlq;

import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("DLQ Operations Persistence & State Machine Integration Tests (PostgreSQL DAO)")
public class DlqOperationsPersistenceIT extends DockerProperties {

    @Autowired
    private DlqOperationsDao dlqDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("Should insert and retrieve DLQ event by ID")
    void shouldInsertAndRetrieveById() {
        UUID id = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent event = new DlqEvent(
                id, operationId, userId, "commands.deposit",
                DlqStatus.PENDING, "DB lock timeout", "{\"amount\": 100}",
                0, null, now, null,
                DlqFailureType.TRANSIENT, "Deposit"
        );

        dlqDao.insert(event);

        Optional<DlqEvent> retrievedOpt = dlqDao.findById(id);
        assertThat(retrievedOpt).isPresent();
        DlqEvent retrieved = retrievedOpt.get();

        assertThat(retrieved.id()).isEqualTo(id);
        assertThat(retrieved.operationId()).isEqualTo(operationId);
        assertThat(retrieved.status()).isEqualTo(DlqStatus.PENDING);
        assertThat(retrieved.retryCount()).isEqualTo(0);
        assertThat(retrieved.failureType()).isEqualTo(DlqFailureType.TRANSIENT);
    }

    @Test
    @DisplayName("Should transition from FAILED to EXHAUSTED after 3 failed attempts (I-DLQ-001)")
    void shouldTransitionToExhaustedAfter3Retries() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent event = new DlqEvent(
                id, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.PENDING, "DB lock timeout", "{\"amount\": 100}",
                0, null, now, null,
                DlqFailureType.TRANSIENT, "Deposit"
        );
        dlqDao.insert(event);

        // Attempt 1: FAILED, retry_count = 1
        dlqDao.markFailed(id, now, DlqFailureType.TRANSIENT);
        DlqEvent after1 = dlqDao.findById(id).orElseThrow();
        assertThat(after1.status()).isEqualTo(DlqStatus.FAILED);
        assertThat(after1.retryCount()).isEqualTo(1);
        assertThat(after1.nextRetryAt()).isNotNull();

        // Attempt 2: FAILED, retry_count = 2
        dlqDao.markFailed(id, now, DlqFailureType.TRANSIENT);
        DlqEvent after2 = dlqDao.findById(id).orElseThrow();
        assertThat(after2.status()).isEqualTo(DlqStatus.FAILED);
        assertThat(after2.retryCount()).isEqualTo(2);
        assertThat(after2.nextRetryAt()).isNotNull();

        // Attempt 3: EXHAUSTED, retry_count = 3, next_retry_at = NULL
        dlqDao.markFailed(id, now, DlqFailureType.TRANSIENT);
        DlqEvent after3 = dlqDao.findById(id).orElseThrow();
        assertThat(after3.status()).isEqualTo(DlqStatus.EXHAUSTED);
        assertThat(after3.retryCount()).isEqualTo(3);
        assertThat(after3.nextRetryAt()).isNull();

        // Automatic claimBatch should NOT claim this exhausted record
        List<DlqEvent> claimed = dlqDao.claimBatch(now.plusSeconds(3600), 50);
        assertThat(claimed).isEmpty();

        // findExhaustedOperations should return this record
        List<DlqEvent> exhaustedList = dlqDao.findExhaustedOperations(50);
        assertThat(exhaustedList).hasSize(1);
        assertThat(exhaustedList.getFirst().id()).isEqualTo(id);
    }

    @Test
    @DisplayName("Should mark operation as DISCARDED")
    void shouldMarkAsDiscarded() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        DlqEvent event = new DlqEvent(
                id, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.PENDING, "Poison payload", "{}",
                0, null, now, null,
                DlqFailureType.POISON, "Deposit"
        );
        dlqDao.insert(event);

        dlqDao.markAsDiscarded(id, now, "Operator dismissed malformed message");

        DlqEvent discarded = dlqDao.findById(id).orElseThrow();
        assertThat(discarded.status()).isEqualTo(DlqStatus.DISCARDED);
        assertThat(discarded.error()).isEqualTo("Operator dismissed malformed message");
        assertThat(discarded.processedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should filter DLQ operations by status and failureType")
    void shouldFilterOperations() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Instant now = Instant.now();

        dlqDao.insert(new DlqEvent(id1, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit", DlqStatus.EXHAUSTED, "err", "{}", 3, null, now, null, DlqFailureType.TRANSIENT, "Deposit"));
        dlqDao.insert(new DlqEvent(id2, UUID.randomUUID(), UUID.randomUUID(), "commands.transfer", DlqStatus.COMPLETED, "err", "{}", 1, null, now, now, DlqFailureType.BUSINESS, "Transfer"));

        List<DlqEvent> exhausted = dlqDao.findByFilter(new DlqQueryFilter(DlqStatus.EXHAUSTED, null, null, null), 10, 0);
        assertThat(exhausted).hasSize(1);
        assertThat(exhausted.getFirst().id()).isEqualTo(id1);

        List<DlqEvent> completed = dlqDao.findByFilter(new DlqQueryFilter(DlqStatus.COMPLETED, null, null, null), 10, 0);
        assertThat(completed).hasSize(1);
        assertThat(completed.getFirst().id()).isEqualTo(id2);
    }
}
