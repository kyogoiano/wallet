package br.com.wallet.integration.fraud.embeddings;

import br.com.wallet.fraud.embeddings.internal.queue.EmbeddingJob;
import br.com.wallet.fraud.embeddings.internal.queue.EmbeddingJobStatus;
import br.com.wallet.fraud.embeddings.internal.queue.PostgresEmbeddingJobDao;
import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.internal.persistence.PostgresFraudRelationshipDao;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("PostgresEmbeddingJobDao Integration Tests (Active Deduplication, Token Leases & State Transitions)")
public class PostgresEmbeddingJobDaoIT extends DockerProperties {

    @Autowired
    private PostgresEmbeddingJobDao dao;

    @Autowired
    private PostgresFraudRelationshipDao relationshipDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-VEC-011: Should enqueue job and reject active duplicates via partial unique index")
    void shouldEnqueueAndRejectActiveDuplicates() {
        UUID entityId = UUID.randomUUID();
        relationshipDao.upsertEntity(new FraudEntity(
            entityId, EntityType.USER, 0.0, 0.0, 0.0, 0.0, 0.0, Instant.now(), Instant.now(), null
        ));

        boolean firstEnqueue = dao.enqueueJob(entityId, "v1", Instant.now());
        assertThat(firstEnqueue).isTrue();

        // Second enqueue while first is active must be rejected
        boolean duplicateEnqueue = dao.enqueueJob(entityId, "v1", Instant.now());
        assertThat(duplicateEnqueue).isFalse();
    }

    @Test
    @DisplayName("REQ-VEC-011: Should claim job with worker token and complete successfully")
    void shouldClaimAndCompleteJob() {
        UUID entityId = UUID.randomUUID();
        relationshipDao.upsertEntity(new FraudEntity(
            entityId, EntityType.USER, 0.0, 0.0, 0.0, 0.0, 0.0, Instant.now(), Instant.now(), null
        ));

        dao.enqueueJob(entityId, "v1", Instant.now());

        UUID workerToken = UUID.randomUUID();
        Optional<EmbeddingJob> claimed = dao.claimNextJob(workerToken, Duration.ofSeconds(30));

        assertThat(claimed).isPresent();
        EmbeddingJob job = claimed.get();
        assertThat(job.entityId()).isEqualTo(entityId);
        assertThat(job.status()).isEqualTo(EmbeddingJobStatus.RUNNING);
        assertThat(job.workerToken()).isEqualTo(workerToken);
        assertThat(job.leaseUntil()).isNotNull();

        boolean completed = dao.completeJob(job.id(), workerToken);
        assertThat(completed).isTrue();

        // Now that status is COMPLETED, a new job can be enqueued for the entity
        boolean newJobEnqueued = dao.enqueueJob(entityId, "v1", Instant.now());
        assertThat(newJobEnqueued).isTrue();
    }
}
