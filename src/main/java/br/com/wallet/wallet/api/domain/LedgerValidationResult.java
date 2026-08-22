package br.com.wallet.wallet.api.domain;

public record LedgerValidationResult(
        boolean valid,
        long corruptedDataSize,
        String error
) {}
