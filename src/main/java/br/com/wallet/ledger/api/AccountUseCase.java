package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.domain.Account;

import java.util.List;
import java.util.UUID;

public interface AccountUseCase {
    Account find(UUID walletId);
    List<Account> list(Integer limit, Integer offset);
}
