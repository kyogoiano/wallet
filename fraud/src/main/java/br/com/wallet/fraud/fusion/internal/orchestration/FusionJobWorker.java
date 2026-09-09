package br.com.wallet.fraud.fusion.internal.orchestration;

import br.com.wallet.fraud.fusion.api.FraudSignalFusionService;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJob;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobRepository;
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
 * Hand-rolled asynchronous worker for consuming durable evaluation jobs (REQ-FUSION-004).
 */
@Component
public class FusionJobWorker {

    private static final Logger log = LoggerFactory.getLogger(FusionJobWorker.class);

    private final FusionJobRepository repository;
    private final FraudSignalFusionService fusionService;
    private final UUID workerToken = UUID.randomUUID();
    private final Duration leaseDuration = Duration.ofSeconds(60);

    public FusionJobWorker(
        @NonNull final FusionJobRepository repository,
        @NonNull final FraudSignalFusionService fusionService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.fusionService = Objects.requireNonNull(fusionService, "fusionService cannot be null");
    }

    @NonNull
    public UUID workerToken() {
        return workerToken;
    }

    @Scheduled(fixedDelay = 1000)
    public boolean pollAndExecute() {
        Optional<FusionJob> jobOpt = repository.claimNextJob(workerToken, leaseDuration);
        if (jobOpt.isEmpty()) {
            return false;
        }

        FusionJob job = jobOpt.get();
        log.debug("Claimed fusion job {} for entity {}", job.jobId(), job.entityId());

        try {
            fusionService.evaluateEntity(job.entityId());
            repository.completeJob(job.jobId(), workerToken);
            return true;
        } catch (Exception e) {
            log.error("Failed executing fusion job {}", job.jobId(), e);
            repository.rescheduleJob(job.jobId(), Duration.ofSeconds(30), e.getMessage() != null ? e.getMessage() : "Unknown error");
            return false;
        }
    }
}
