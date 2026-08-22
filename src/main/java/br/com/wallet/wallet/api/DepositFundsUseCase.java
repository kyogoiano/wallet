package br.com.wallet.wallet.api;

import br.com.wallet.wallet.api.context.Deposit;

public interface DepositFundsUseCase  extends UseCase<Deposit> {
    @Override
    void handle(Deposit deposit);
}
