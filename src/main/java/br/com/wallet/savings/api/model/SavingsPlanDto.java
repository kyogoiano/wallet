package br.com.wallet.savings.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavingsPlanDto(
    UUID id,
    UUID sourceWalletId,
    UUID targetWalletId,
    BigDecimal minimumRetainedBalance,
    String status,
    List<SavingsRuleDto> rules,
    Instant createdAt,
    Instant updatedAt
) {}
