package br.com.wallet.integration.fraud.fusion;

import br.com.wallet.fraud.fusion.internal.persistence.FusionJob;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobStatus;
import br.com.wallet.fraud.fusion.internal.persistence.PostgresFusionJobDao;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("PostgresFusionJobDao Integration Tests (REQ-FUSION-004, REQ-FUSION-009, REQ-FUSION-013)")
public class PostgresFusionJobDaoIT extends DockerProperties {

    @Autowired
    private PostgresFusionJobDao dao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-FUSION-009: Coalescing — Multiple pending events coalesce into single job updating as_of and payload")
    void shouldCoalesceMultiplePendingEventsIntoLatestAsOf() {
        UUID entityId = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now();

        // Event 1
        UUID jobId1 = dao.enqueueOrCoalesceJob(entityId, "v1", t1, "{\"event\":\"event_1\"}");
        assertThat(jobId1).isNotNull();

        // Event 2 while first is still PENDING -> Coalesce into existing job
        UUID jobId2 = dao.enqueueOrCoalesceJob(entityId, "v1", t2, "{\"event\":\"event_2\"}");
        assertThat(jobId2).isEqualTo(jobId1);

        List<FusionJob> jobs = dao.findByEntityId(entityId);
        assertThat(jobs).hasSize(1);

        FusionJob coalesced = jobs.getFirst();
        assertThat(coalesced.status()).isEqualTo(FusionJobStatus.PENDING);
        assertThat(coalesced.asOf()).isAfterOrEqualTo(t1);
        assertThat(coalesced.payload()).contains("event_2");
    }

    @Test
    @DisplayName("REQ-FUSION-009: Clean Insert When Running — Event arriving while RUNNING cleanly creates new PENDING job")
    void shouldAllowNewPendingJobWhenJobIsRunning() {
        UUID entityId = UUID.randomUUID();
        UUID workerToken = UUID.randomUUID();

        // Step 1: Enqueue first job
        UUID job1Id = dao.enqueueOrCoalesceJob(entityId, "v1", Instant.now(), "{\"phase\":1}");

        // Step 2: Worker claims job 1 -> status becomes RUNNING
        Optional<FusionJob> claimed = dao.claimNextJob(workerToken, Duration.ofSeconds(30));
        assertThat(claimed).isPresent();
        assertThat(claimed.get().jobId()).isEqualTo(job1Id);
        assertThat(claimed.get().status()).isEqualTo(FusionJobStatus.RUNNING);

        // Step 3: Second event arrives while job 1 is RUNNING -> New PENDING job must be created
        UUID job2Id = dao.enqueueOrCoalesceJob(entityId, "v1", Instant.now(), "{\"phase\":2}");
        assertThat(job2Id).isNotEqualTo(job1Id);

        List<FusionJob> jobs = dao.findByEntityId(entityId);
        assertThat(jobs).hasSize(2);

        // One is RUNNING, one is PENDING
        long runningCount = jobs.stream().filter(j -> j.status() == FusionJobStatus.RUNNING).count();
        long pendingCount = jobs.stream().filter(j -> j.status() == FusionJobStatus.PENDING).count();
        assertThat(runningCount).isEqualTo(1);
        assertThat(pendingCount).isEqualTo(1);

        // Step 4: Complete job 1
        boolean completed = dao.completeJob(job1Id, workerToken);
        assertThat(completed).isTrue();

        // Step 5: Worker claims next job -> gets job 2
        Optional<FusionJob> claimed2 = dao.claimNextJob(workerToken, Duration.ofSeconds(30));
        assertThat(claimed2).isPresent();
        assertThat(claimed2.get().jobId()).isEqualTo(job2Id);
    }

    @Test
    @DisplayName("REQ-FUSION-004: Worker Acquisition — Claim and complete with SKIP LOCKED concurrency")
    void shouldAcquireAndCompleteJobWithSkipLocked() {
        UUID entityId = UUID.randomUUID();
        UUID worker1 = UUID.randomUUID();
        UUID worker2 = UUID.randomUUID();

        UUID jobId = dao.enqueueOrCoalesceJob(entityId, "v1", Instant.now(), "{}");

        // Worker 1 claims the job
        Optional<FusionJob> jobW1 = dao.claimNextJob(worker1, Duration.ofSeconds(60));
        assertThat(jobW1).isPresent();
        assertThat(jobW1.get().jobId()).isEqualTo(jobId);
        assertThat(jobW1.get().workerToken()).isEqualTo(worker1);

        // Worker 2 tries to claim concurrently -> empty
        Optional<FusionJob> jobW2 = dao.claimNextJob(worker2, Duration.ofSeconds(60));
        assertThat(jobW2).isEmpty();

        // Worker 1 completes the job
        boolean completed = dao.completeJob(jobId, worker1);
        assertThat(completed).isTrue();

        Optional<FusionJob> finalState = dao.findById(jobId);
        assertThat(finalState).isPresent();
        assertThat(finalState.get().status()).isEqualTo(FusionJobStatus.COMPLETED);
    }
}
