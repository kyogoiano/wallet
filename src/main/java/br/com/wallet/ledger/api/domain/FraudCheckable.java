package br.com.wallet.ledger.api.domain;

import java.math.BigDecimal;
import java.util.UUID;

public interface FraudCheckable {
    UUID operationId();
    BigDecimal amount();
    UUID sourceUserIdForFraudCheck(); // e.g., walletId for Deposit/Withdraw, 'from' for Transfer
    UUID targetUserIdForFraudCheck(); // e.g., 'to' for Transfer, null for Deposit/Withdraw
}
