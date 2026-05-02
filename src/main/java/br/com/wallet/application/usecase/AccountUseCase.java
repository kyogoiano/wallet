package br.com.wallet.application.usecase;

import br.com.wallet.domain.Account;

import java.util.List;
import java.util.UUID;

public interface AccountUseCase {
    Account find(UUID walletId);
    List<Account> list(Integer limit, Integer offset);
}
