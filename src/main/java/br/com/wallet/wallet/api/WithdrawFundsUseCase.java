package br.com.wallet.wallet.api;

import br.com.wallet.wallet.api.context.Withdraw;

public interface WithdrawFundsUseCase extends UseCase<Withdraw> {
    @Override
    void handle(Withdraw withdraw);
}
