package br.com.wallet.wallet.api.domain;

import java.math.BigDecimal;
import java.util.UUID;

public interface FraudCheckable {
    UUID operationId();
    BigDecimal amount();
    UUID getSourceUserIdForFraudCheck(); // e.g., walletId for Deposit/Withdraw, 'from' for Transfer
    UUID getTargetUserIdForFraudCheck(); // e.g., 'to' for Transfer, null for Deposit/Withdraw
}
