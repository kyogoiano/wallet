package br.com.wallet.unit.edge;

import br.com.wallet.edge.api.EdgeReadinessState;
import br.com.wallet.edge.internal.recovery.EdgeReadinessHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EdgeReadinessHealthIndicator Reactive Health Unit Tests (I-EDGE-004)")
class EdgeReadinessHealthIndicatorTest {

    private EdgeReadinessHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new EdgeReadinessHealthIndicator();
    }

    @Test
    @DisplayName("Should emit OUT_OF_SERVICE Mono<Health> when INITIALIZING")
    void shouldEmitOutOfServiceWhenInitializing() {
        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
                    assertThat(health.getDetails()).containsEntry("edgeState", "INITIALIZING");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit OUT_OF_SERVICE Mono<Health> when RECOVERING")
    void shouldEmitOutOfServiceWhenRecovering() {
        indicator.transitionTo(EdgeReadinessState.RECOVERING, "Scanning segments");

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
                    assertThat(health.getDetails()).containsEntry("edgeState", "RECOVERING");
                    assertThat(health.getDetails()).containsEntry("reason", "Scanning segments");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit UP Mono<Health> when transitioned to READY")
    void shouldEmitUpWhenReady() {
        indicator.transitionTo(EdgeReadinessState.READY, "Operational");

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus()).isEqualTo(Status.UP);
                    assertThat(health.getDetails()).containsEntry("edgeState", "READY");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should emit DEGRADED Mono<Health> when disk corruption is isolated")
    void shouldEmitDegradedWhenCorruptionIsolated() {
        indicator.transitionTo(EdgeReadinessState.DEGRADED, "Corrupted segment quarantined");

        StepVerifier.create(indicator.health())
                .assertNext(health -> {
                    assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
                    assertThat(health.getDetails()).containsEntry("edgeState", "DEGRADED");
                })
                .verifyComplete();
    }
}
