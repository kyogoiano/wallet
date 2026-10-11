package br.com.wallet.integration.ledger.scenarios;

import java.math.BigDecimal;

public record TransferScenario(
        BigDecimal initialFrom,
        BigDecimal transferAmount,
        BigDecimal expectedFrom,
        BigDecimal expectedTo
) {}
