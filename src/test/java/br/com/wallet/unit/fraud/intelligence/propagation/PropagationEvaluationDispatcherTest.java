package br.com.wallet.unit.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.internal.propagation.DefaultPropagationDispatcher;
import br.com.wallet.fraud.intelligence.internal.propagation.PropagationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PropagationEvaluationDispatcher Unit Tests (Dispatch & Deduplication)")
class PropagationEvaluationDispatcherTest {

    private PropagationJobRepository jobRepository;
    private DefaultPropagationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        jobRepository = Mockito.mock(PropagationJobRepository.class);
        dispatcher = new DefaultPropagationDispatcher(jobRepository);
    }

    @Test
    @DisplayName("Should successfully enqueue new propagation job")
    void shouldEnqueueNewJob() {
        UUID entityId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Instant asOf = Instant.now();

        when(jobRepository.enqueue(eq(entityId), eq(asOf), eq("v1")))
            .thenReturn(Optional.of(jobId));

        Optional<UUID> result = dispatcher.dispatch(entityId, asOf, "v1");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(jobId);
        verify(jobRepository).enqueue(eq(entityId), eq(asOf), eq("v1"));
    }

    @Test
    @DisplayName("I-PROP-007: Should return empty if duplicate active job exists")
    void shouldDeduplicateActiveJobsIdempotently() {
        UUID entityId = UUID.randomUUID();

        when(jobRepository.enqueue(eq(entityId), any(Instant.class), eq("v1")))
            .thenReturn(Optional.empty());

        Optional<UUID> result = dispatcher.dispatch(entityId, "v1");

        assertThat(result).isEmpty();
    }
}
