package br.com.wallet.decision.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Normalized scalar risk or confidence score in the closed interval [0.00, 1.00] with scale 2.
 *
 * @param score normalized risk intensity score
 * @param rationale rationale or evidence explanation
 */
public record ScoreDecision(
    BigDecimal score,
    String rationale
) implements DecisionValue {

    public ScoreDecision {
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(rationale, "rationale must not be null");

        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("score must be in range [0.00, 1.00]: " + score);
        }

        score = score.setScale(2, RoundingMode.HALF_UP);
    }
}
