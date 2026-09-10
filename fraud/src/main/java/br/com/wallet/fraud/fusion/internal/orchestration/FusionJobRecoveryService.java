package br.com.wallet.fraud.fusion.internal.orchestration;

import br.com.wallet.fraud.fusion.internal.persistence.FusionJob;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Recovers expired job leases using a bounded exponential backoff schedule (REQ-FUSION-013, I-FUSION-009).
 */
@Service
public class FusionJobRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(FusionJobRecoveryService.class);
    public static final int MAX_ATTEMPTS = 5;

    private final FusionJobRepository repository;

    public FusionJobRecoveryService(@NonNull final FusionJobRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * Bounded exponential backoff schedule per REQ-FUSION-013:
     * Attempt 1: 5s
     * Attempt 2: 30s
     * Attempt 3: 2m (120s)
     * Attempt 4: 10m (600s)
     * Attempt 5+: FAILED (handled by caller)
     */
    @NonNull
    public Duration calculateBackoff(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofSeconds(5);
            case 2 -> Duration.ofSeconds(30);
            case 3 -> Duration.ofMinutes(2);
            default -> Duration.ofMinutes(10);
        };
    }

    @Scheduled(fixedDelayString = "${fraud.fusion.recovery.fixed-delay:5000}", initialDelayString = "${fraud.fusion.recovery.initial-delay:5000}")
    public int recoverExpiredLeases() {
        return recoverExpiredLeases(Instant.now());
    }

    public int recoverExpiredLeases(@NonNull final Instant now) {
        Objects.requireNonNull(now, "now cannot be null");

        List<FusionJob> expiredJobs = repository.findExpiredRunningJobs(now);
        for (FusionJob job : expiredJobs) {
            if (job.attemptCount() >= MAX_ATTEMPTS) {
                log.warn("Job {} reached max attempts ({}), marking FAILED", job.jobId(), job.attemptCount());
                repository.markJobFailed(job.jobId(), "Lease expired and attempt count exceeded maximum of " + MAX_ATTEMPTS);
            } else {
                Duration backoff = calculateBackoff(job.attemptCount());
                log.info("Job {} lease expired on attempt {}, rescheduling with backoff {}", job.jobId(), job.attemptCount(), backoff);
                repository.rescheduleJob(job.jobId(), backoff, "Lease expired during execution on attempt " + job.attemptCount());
            }
        }
        return expiredJobs.size();
    }
}
