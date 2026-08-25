package br.com.wallet.savings.internal.engine;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public final class ThresholdCalculator {

    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    @NonNull
    public BigDecimal calculate(@NonNull final BigDecimal currentBalance, @NonNull final BigDecimal ceilingThreshold) {
        Objects.requireNonNull(currentBalance, "currentBalance cannot be null");
        Objects.requireNonNull(ceilingThreshold, "ceilingThreshold cannot be null");

        if (ceilingThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ceiling threshold must be strictly positive, given: " + ceilingThreshold);
        }

        BigDecimal excess = currentBalance.subtract(ceilingThreshold).setScale(MONETARY_SCALE, ROUNDING_MODE);

        return excess.compareTo(BigDecimal.ZERO) > 0
                ? excess
                : BigDecimal.ZERO.setScale(MONETARY_SCALE, ROUNDING_MODE);
    }
}
