package br.com.wallet.edge.internal.recovery;

import br.com.wallet.edge.api.EdgeReadinessState;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive Actuator HealthIndicator implementing I-EDGE-004.
 * Non-blocking readiness probe reporting OUT_OF_SERVICE during recovery scan, and UP once operational.
 */
public class EdgeReadinessHealthIndicator implements ReactiveHealthIndicator {

    private final AtomicReference<EdgeReadinessState> state = new AtomicReference<>(EdgeReadinessState.INITIALIZING);
    private final AtomicReference<String> detailMessage = new AtomicReference<>("Initializing gateway");

    @Override
    public @NullMarked Mono<Health> health() {
        return Mono.fromSupplier(this::computeHealth);
    }

    private Health computeHealth() {
        EdgeReadinessState current = state.get();
        return switch (current) {
            case READY -> Health.up().withDetail("edgeState", current.name()).build();
            case RECOVERING, INITIALIZING -> Health.outOfService()
                    .withDetail("edgeState", current.name())
                    .withDetail("reason", detailMessage.get())
                    .build();
            case DEGRADED -> Health.status("DEGRADED")
                    .withDetail("edgeState", current.name())
                    .withDetail("reason", detailMessage.get())
                    .build();
        };
    }

    public void transitionTo(EdgeReadinessState newState, String reason) {
        this.state.set(newState);
        this.detailMessage.set(reason);
    }

    public EdgeReadinessState getCurrentState() {
        return state.get();
    }
}
