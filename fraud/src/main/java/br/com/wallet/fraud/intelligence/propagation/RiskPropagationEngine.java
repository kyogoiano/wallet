package br.com.wallet.fraud.intelligence.propagation;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Public capability interface for evaluating graph-wide risk propagation and temporal decay.
 */
public interface RiskPropagationEngine {

    /**
     * Evaluates topological risk propagation radiating outwards from a root source entity.
     *
     * @param sourceEntityId source entity UUID
     * @param asOf evaluation point in time
     * @return PropagationResult containing mapped target risks
     */
    @NonNull
    PropagationResult evaluateEntity(@NonNull UUID sourceEntityId, @NonNull Instant asOf);

    /**
     * Evaluates risk propagation along paths connecting a specific source and target.
     *
     * @param sourceEntityId source entity UUID
     * @param targetEntityId target entity UUID
     * @param asOf evaluation point in time
     * @return PropagationResult containing target risk
     */
    @NonNull
    PropagationResult evaluatePath(@NonNull UUID sourceEntityId, @NonNull UUID targetEntityId, @NonNull Instant asOf);
}
