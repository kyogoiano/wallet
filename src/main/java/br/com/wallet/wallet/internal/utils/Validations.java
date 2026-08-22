package br.com.wallet.wallet.internal.utils;

import java.math.BigDecimal;

public class Validations {
    public static void validatePositiveAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
