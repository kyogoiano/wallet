package br.com.wallet.savings.internal.engine;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public final class RoundUpCalculator {

    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    @NonNull
    public BigDecimal calculate(@NonNull final BigDecimal amount, @NonNull final BigDecimal step) {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(step, "step cannot be null");

        if (step.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Step amount must be strictly positive, given: " + step);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONETARY_SCALE, ROUNDING_MODE);
        }

        BigDecimal quotient = amount.divide(step, 0, RoundingMode.CEILING);
        BigDecimal targetCeiling = quotient.multiply(step).setScale(MONETARY_SCALE, ROUNDING_MODE);
        BigDecimal delta = targetCeiling.subtract(amount).setScale(MONETARY_SCALE, ROUNDING_MODE);

        return delta.compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.ZERO.setScale(MONETARY_SCALE, ROUNDING_MODE)
                : delta;
    }
}
