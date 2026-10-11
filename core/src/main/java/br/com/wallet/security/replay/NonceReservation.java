package br.com.wallet.security.replay;

import java.util.Objects;

/**
 * Sealed algebraic outcome representing the result of a two-phase nonce reservation (REQ-SEC-028, I-ENV-004).
 */
public sealed interface NonceReservation {

    /**
     * Nonce successfully reserved under a temporary lease.
     */
    record Admitted() implements NonceReservation {}

    /**
     * Nonce rejected due to replay detection or active concurrent lease.
     */
    record Rejected(ReplayRejectionReason reason) implements NonceReservation {
        public Rejected {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * Nonce state could not be determined due to storage or network unavailability (fail-closed).
     */
    record Unavailable(ReplayAvailabilityReason reason) implements NonceReservation {
        public Unavailable {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
