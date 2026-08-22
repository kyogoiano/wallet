package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.context.Deposit;

public interface DepositFundsUseCase  extends UseCase<Deposit> {
    @Override
    void handle(Deposit deposit);
}
