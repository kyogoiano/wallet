package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.internal.orchestration.FusionJobRecoveryService;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJob;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobRepository;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FusionJobRecoveryService Unit Tests (REQ-FUSION-013, I-FUSION-009)")
class FusionJobRecoveryServiceTest {

    private FusionJobRepository repository;
    private FusionJobRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        repository = mock(FusionJobRepository.class);
        recoveryService = new FusionJobRecoveryService(repository);
    }

    @ParameterizedTest(name = "Attempt {0} should have backoff of {1}s")
    @CsvSource({
        "1, 5",
        "2, 30",
        "3, 120",
        "4, 600",
        "5, 600"
    })
    @DisplayName("REQ-FUSION-013: Backoff Schedule Calculation")
    void shouldCalculateCorrectBackoff(int attempt, long expectedSeconds) {
        Duration backoff = recoveryService.calculateBackoff(attempt);
        assertThat(backoff).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    @DisplayName("I-FUSION-009: Reclaims expired jobs applying backoff and marks FAILED when max attempts reached")
    void shouldReclaimExpiredRunningJobsWithBackoff() {
        Instant now = Instant.now();
        UUID job1Id = UUID.randomUUID();
        UUID job2Id = UUID.randomUUID();

        FusionJob expiredJobUnderLimit = new FusionJob(
            job1Id,
            UUID.randomUUID(),
            "v1",
            FusionJobStatus.RUNNING,
            now.minusSeconds(120),
            2,
            UUID.randomUUID(),
            now.minusSeconds(10),
            null,
            null,
            "{}",
            null,
            now.minusSeconds(120),
            now.minusSeconds(10)
        );

        FusionJob expiredJobAtMax = new FusionJob(
            job2Id,
            UUID.randomUUID(),
            "v1",
            FusionJobStatus.RUNNING,
            now.minusSeconds(600),
            5,
            UUID.randomUUID(),
            now.minusSeconds(20),
            null,
            null,
            "{}",
            null,
            now.minusSeconds(600),
            now.minusSeconds(20)
        );

        when(repository.findExpiredRunningJobs(any(Instant.class)))
            .thenReturn(List.of(expiredJobUnderLimit, expiredJobAtMax));

        int reclaimed = recoveryService.recoverExpiredLeases(now);

        assertThat(reclaimed).isEqualTo(2);

        // Job 1 (attempt 2 < 5) should be scheduled for RETRY_WAIT with 30s backoff (attempt 2 backoff)
        verify(repository).rescheduleJob(eq(job1Id), eq(Duration.ofSeconds(30)), any(String.class));

        // Job 2 (attempt 5 >= 5) should be marked FAILED
        verify(repository).markJobFailed(eq(job2Id), any(String.class));
    }
}
