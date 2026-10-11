package br.com.wallet.fraud.investigation.spi;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Objects;

public record InferenceBenchmarkThresholds(
    @NonNull Duration maxP95Latency,
    @NonNull Duration maxTimeout,
    double minJsonValidityRate,
    double minGroundingValidityRate
) {
    public InferenceBenchmarkThresholds {
        Objects.requireNonNull(maxP95Latency, "maxP95Latency cannot be null");
        Objects.requireNonNull(maxTimeout, "maxTimeout cannot be null");
    }

    public static InferenceBenchmarkThresholds defaultThresholds() {
        return new InferenceBenchmarkThresholds(Duration.ofSeconds(2), Duration.ofSeconds(5), 1.0, 1.0);
    }
}
