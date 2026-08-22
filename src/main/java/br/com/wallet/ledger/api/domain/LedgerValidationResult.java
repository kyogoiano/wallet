package br.com.wallet.ledger.api.domain;

public record LedgerValidationResult(
        boolean valid,
        long corruptedDataSize,
        String error
) {}
