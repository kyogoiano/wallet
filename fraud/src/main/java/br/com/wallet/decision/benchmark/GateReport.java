package br.com.wallet.decision.benchmark;

import java.util.Objects;

/**
 * Report of an individual evaluation gate assessment.
 */
public record GateReport(
    int gateNumber,
    String gateName,
    boolean passed,
    String diagnostic
) {

    public GateReport {
        Objects.requireNonNull(gateName, "gateName must not be null");
        Objects.requireNonNull(diagnostic, "diagnostic must not be null");
    }
}
