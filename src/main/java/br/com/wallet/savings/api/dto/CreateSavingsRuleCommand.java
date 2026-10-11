package br.com.wallet.savings.api.dto;

import br.com.wallet.savings.api.model.SavingsRuleType;
import java.math.BigDecimal;

public record CreateSavingsRuleCommand(
    SavingsRuleType ruleType,
    BigDecimal stepAmount,
    BigDecimal percentageRate,
    BigDecimal ceilingThreshold
) {}
