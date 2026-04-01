package br.com.wallet.application.usecase;

import br.com.wallet.domain.context.Withdraw;

public interface WithdrawFundsUseCase {
    void handle(Withdraw withdraw);
}
