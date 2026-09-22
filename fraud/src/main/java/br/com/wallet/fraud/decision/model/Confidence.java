package br.com.wallet.fraud.decision.model;

import java.math.BigDecimal;

/**
 * Calibrated semantic confidence level representing the probabilistic reliability of a decision.
 */
public enum Confidence {
    HIGH(new BigDecimal("0.85"), new BigDecimal("1.00")),
    MEDIUM(new BigDecimal("0.50"), new BigDecimal("0.85")),
    LOW(new BigDecimal("0.00"), new BigDecimal("0.50"));

    private final BigDecimal minProbability;
    private final BigDecimal maxProbability;

    Confidence(final BigDecimal minProbability, final BigDecimal maxProbability) {
        this.minProbability = minProbability;
        this.maxProbability = maxProbability;
    }

    public BigDecimal minProbability() {
        return minProbability;
    }

    public BigDecimal maxProbability() {
        return maxProbability;
    }

    /**
     * Maps an empirical probability score to calibrated Confidence level.
     */
    public static Confidence fromProbability(final BigDecimal probability) {
        if (probability == null) {
            return LOW;
        }
        if (probability.compareTo(HIGH.minProbability) >= 0) {
            return HIGH;
        }
        if (probability.compareTo(MEDIUM.minProbability) >= 0) {
            return MEDIUM;
        }
        return LOW;
    }
}
