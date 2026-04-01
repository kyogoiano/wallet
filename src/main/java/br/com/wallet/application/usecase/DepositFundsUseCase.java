package br.com.wallet.application.usecase;

import br.com.wallet.domain.context.Deposit;

public interface DepositFundsUseCase {
    void handle(Deposit deposit);
}
