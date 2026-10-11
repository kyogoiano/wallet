package br.com.wallet.edge.internal.journal;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Enforces spool capacity watermarks with admission hysteresis (REQ-EDG-003, I-EDGE-005).
 * - Saturated at >= 95.0%
 * - Recovery required to drop below 85.0% before re-enabling degraded acceptance.
 */
public class SpoolWatermarkGate {

    public static final double WARNING_THRESHOLD = 70.0;
    public static final double PRESSURE_THRESHOLD = 80.0;
    public static final double SATURATED_THRESHOLD = 95.0;
    public static final double RECOVERY_THRESHOLD = 85.0;
    public static final int RETRY_AFTER_SECONDS = 5;

    private final AtomicReference<SpoolAdmissionState> currentState = new AtomicReference<>(SpoolAdmissionState.NORMAL);

    public SpoolAdmissionState evaluate(double usagePercent) {
        return currentState.updateAndGet(current -> {
            if (current == SpoolAdmissionState.SATURATED) {
                if (usagePercent < RECOVERY_THRESHOLD) {
                    if (usagePercent >= PRESSURE_THRESHOLD) {
                        return SpoolAdmissionState.PRESSURE;
                    } else if (usagePercent >= WARNING_THRESHOLD) {
                        return SpoolAdmissionState.WARNING;
                    } else {
                        return SpoolAdmissionState.NORMAL;
                    }
                }
                return SpoolAdmissionState.SATURATED;
            }

            if (usagePercent >= SATURATED_THRESHOLD) {
                return SpoolAdmissionState.SATURATED;
            } else if (usagePercent >= PRESSURE_THRESHOLD) {
                return SpoolAdmissionState.PRESSURE;
            } else if (usagePercent >= WARNING_THRESHOLD) {
                return SpoolAdmissionState.WARNING;
            } else {
                return SpoolAdmissionState.NORMAL;
            }
        });
    }

    public boolean isDegradedAcceptanceAllowed() {
        return currentState.get() != SpoolAdmissionState.SATURATED;
    }

    public SpoolAdmissionState getCurrentState() {
        return currentState.get();
    }

    public int retryAfterSeconds() {
        return RETRY_AFTER_SECONDS;
    }
}
