package br.com.wallet.fraud.embeddings.internal.queue;

import br.com.wallet.fraud.embeddings.api.EmbeddingEvaluationDispatcher;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class DefaultEmbeddingDispatcher implements EmbeddingEvaluationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmbeddingDispatcher.class);
    private static final String DEFAULT_MODEL_VERSION = "v1";

    private final EmbeddingJobRepository jobRepository;

    public DefaultEmbeddingDispatcher(@NonNull final EmbeddingJobRepository jobRepository) {
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository cannot be null");
    }

    @Override
    public boolean dispatchEvaluation(@NonNull final UUID entityId, @NonNull final Instant asOf) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(asOf, "asOf cannot be null");

        boolean enqueued = jobRepository.enqueueJob(entityId, DEFAULT_MODEL_VERSION, asOf);
        if (enqueued) {
            log.info("Enqueued embedding evaluation job for entity: {} as_of: {}", entityId, asOf);
        } else {
            log.debug("Active embedding job already exists for entity: {}, skipping duplicate", entityId);
        }
        return enqueued;
    }

    @Override
    public boolean dispatchEvaluation(@NonNull final UUID entityId) {
        return dispatchEvaluation(entityId, Instant.now());
    }
}
