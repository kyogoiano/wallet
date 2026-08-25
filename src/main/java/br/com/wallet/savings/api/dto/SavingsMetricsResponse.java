package br.com.wallet.savings.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record SavingsMetricsResponse(
    UUID walletId,
    BigDecimal totalSaved,
    long executionCount,
    Map<String, BigDecimal> savedByRuleType
) {}
