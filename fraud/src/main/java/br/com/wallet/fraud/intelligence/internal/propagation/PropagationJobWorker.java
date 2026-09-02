package br.com.wallet.fraud.intelligence.internal.propagation;

import br.com.wallet.fraud.intelligence.propagation.PropagationResult;
import br.com.wallet.fraud.intelligence.propagation.RiskPropagationEngine;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Background worker pool for processing claimed propagation jobs with token lease heartbeats.
 */
@Component
public class PropagationJobWorker {

    private static final Logger log = LoggerFactory.getLogger(PropagationJobWorker.class);

    private final PropagationJobRepository jobRepository;
    private final RiskPropagationEngine propagationEngine;
    private final UUID workerToken = UUID.randomUUID();
    private final Duration leaseDuration = Duration.ofMinutes(2);
    private static final int MAX_ATTEMPTS = 5;

    public PropagationJobWorker(
        @NonNull final PropagationJobRepository jobRepository,
        @NonNull final RiskPropagationEngine propagationEngine
    ) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository cannot be null");
        this.propagationEngine = Objects.requireNonNull(propagationEngine, "propagationEngine cannot be null");
    }

    public UUID getWorkerToken() {
        return workerToken;
    }

    /**
     * Claims and processes a single eligible propagation job.
     *
     * @return true if a job was processed, false if the queue was empty
     */
    public boolean pollAndExecuteNext() {
        Optional<PropagationJob> claimedOpt = jobRepository.claimNext(workerToken, leaseDuration);
        if (claimedOpt.isEmpty()) {
            return false;
        }

        PropagationJob job = claimedOpt.get();
        log.debug("Worker [{}] claimed propagation job id={} entity={} asOf={}",
            workerToken, job.id(), job.entityId(), job.asOf());

        try {
            PropagationResult result = propagationEngine.evaluateEntity(job.entityId(), job.asOf());
            boolean completed = jobRepository.complete(job.id(), workerToken);
            if (completed) {
                log.info("Worker [{}] completed propagation job id={} entity={} (evaluated {} paths, {} targets)",
                    workerToken, job.id(), job.entityId(), result.pathsEvaluated(), result.propagatedRisks().size());
            } else {
                log.warn("Worker [{}] failed to complete job id={} (lease may have expired)", workerToken, job.id());
            }
            return true;
        } catch (Exception e) {
            log.error("Worker [{}] encountered error executing job id={} entity={}: {}",
                workerToken, job.id(), job.entityId(), e.getMessage(), e);

            if (job.attemptCount() < MAX_ATTEMPTS) {
                long delaySeconds = (long) Math.pow(2, job.attemptCount());
                var propagatedJobScheduled = jobRepository.scheduleRetry(job.id(), workerToken, e.getMessage(), Duration.ofSeconds(delaySeconds));
                log.info("Worker scheduled jobs rows: {}",  propagatedJobScheduled);
            } else {
                jobRepository.fail(job.id(), workerToken, e.getMessage());
            }
            return true;
        }
    }

    /**
     * Scheduled sweep claiming pending jobs.
     */
    @Scheduled(fixedDelayString = "${fraud.propagation.worker.poll-interval-ms:5000}")
    public void scheduledPoll() {
        int processedCount = 0;
        while (processedCount < 10 && pollAndExecuteNext()) {
            processedCount++;
        }
    }

    /**
     * Scheduled reaper to recover expired leases.
     */
    @Scheduled(fixedDelayString = "${fraud.propagation.worker.reap-interval-ms:60000}")
    public void reapExpiredLeases() {
        int reaped = jobRepository.reapExpiredLeases();
        if (reaped > 0) {
            log.warn("Reaped {} expired propagation job leases back to RETRY_WAIT", reaped);
        }
    }
}
