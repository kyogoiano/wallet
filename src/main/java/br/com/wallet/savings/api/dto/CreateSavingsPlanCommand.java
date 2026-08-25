package br.com.wallet.savings.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateSavingsPlanCommand(
    UUID sourceWalletId,
    UUID targetWalletId,
    BigDecimal minimumRetainedBalance,
    List<CreateSavingsRuleCommand> rules
) {}
