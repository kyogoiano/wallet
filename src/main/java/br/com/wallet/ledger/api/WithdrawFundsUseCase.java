package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.context.Withdraw;

public interface WithdrawFundsUseCase extends UseCase<Withdraw> {
    @Override
    void handle(Withdraw withdraw);
}
