package br.com.wallet.savings.api.model;

import java.math.BigDecimal;
import java.util.UUID;

public record SavingsRuleDto(
    UUID id,
    UUID planId,
    SavingsRuleType ruleType,
    BigDecimal stepAmount,
    BigDecimal percentageRate,
    BigDecimal ceilingThreshold,
    boolean isActive
) {}
