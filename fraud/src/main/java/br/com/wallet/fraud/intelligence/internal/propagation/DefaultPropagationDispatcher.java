package br.com.wallet.fraud.intelligence.internal.propagation;

import br.com.wallet.fraud.intelligence.propagation.PropagationEvaluationDispatcher;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of PropagationEvaluationDispatcher for enqueuing durable propagation jobs.
 */
@Service
public class DefaultPropagationDispatcher implements PropagationEvaluationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultPropagationDispatcher.class);

    private final PropagationJobRepository jobRepository;

    public DefaultPropagationDispatcher(@NonNull final PropagationJobRepository jobRepository) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository cannot be null");
    }

    @Override
    @NonNull
    public Optional<UUID> dispatch(@NonNull UUID entityId, @NonNull String modelVersion) {
        return dispatch(entityId, Instant.now(), modelVersion);
    }

    @Override
    @NonNull
    public Optional<UUID> dispatch(@NonNull UUID entityId, @NonNull Instant asOf, @NonNull String modelVersion) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");
        Objects.requireNonNull(modelVersion, "modelVersion cannot be null");

        Optional<UUID> jobId = jobRepository.enqueue(entityId, asOf, modelVersion);
        if (jobId.isPresent()) {
            log.info("Enqueued fraud risk propagation job id={} for entity={} asOf={} modelVersion={}",
                jobId.get(), entityId, asOf, modelVersion);
        } else {
            log.debug("Active propagation job already exists for entity={} modelVersion={}, skipping duplicate enqueue",
                entityId, modelVersion);
        }
        return jobId;
    }
}
