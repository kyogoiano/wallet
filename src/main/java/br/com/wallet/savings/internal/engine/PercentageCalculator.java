package br.com.wallet.savings.internal.engine;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public final class PercentageCalculator {

    private static final int MONETARY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @NonNull
    public BigDecimal calculate(@NonNull final BigDecimal amount, @NonNull final BigDecimal percentageRate) {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(percentageRate, "percentageRate cannot be null");

        if (percentageRate.compareTo(BigDecimal.ZERO) <= 0 || percentageRate.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Percentage rate must be > 0 and <= 100, given: " + percentageRate);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(MONETARY_SCALE, ROUNDING_MODE);
        }

        return amount.multiply(percentageRate)
                .divide(ONE_HUNDRED, MONETARY_SCALE, ROUNDING_MODE);
    }
}
