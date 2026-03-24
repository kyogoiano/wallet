package br.com.wallet.domain;

public record LedgerValidationResult(
        boolean valid,
        long validatedEntriesSize,
        String error
) {}
