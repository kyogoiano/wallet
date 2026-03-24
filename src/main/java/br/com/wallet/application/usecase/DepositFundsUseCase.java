package br.com.wallet.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public interface DepositFundsUseCase {
    void execute(UUID walletId, BigDecimal amount, UUID operationId);
}
