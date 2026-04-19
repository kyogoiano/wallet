package br.com.wallet.application.usecase;

import br.com.wallet.domain.context.Withdraw;

public interface WithdrawFundsUseCase extends UseCase<Withdraw> {
    @Override
    void handle(Withdraw withdraw);
}
