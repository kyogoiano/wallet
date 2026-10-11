package br.com.wallet.decision.benchmark;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Final certification decision of the Five-Gate Semantic Evaluation Protocol.
 */
public record BenchmarkCertification(
    boolean certified,
    List<GateReport> gateReports,
    BigDecimal candidateF1,
    BigDecimal baselineF1,
    BigDecimal candidateEce,
    BigDecimal baselineEce
) {

    public BenchmarkCertification {
        gateReports = List.copyOf(gateReports != null ? gateReports : List.of());
        Objects.requireNonNull(candidateF1, "candidateF1 must not be null");
        Objects.requireNonNull(baselineF1, "baselineF1 must not be null");
        Objects.requireNonNull(candidateEce, "candidateEce must not be null");
        Objects.requireNonNull(baselineEce, "baselineEce must not be null");
    }
}
