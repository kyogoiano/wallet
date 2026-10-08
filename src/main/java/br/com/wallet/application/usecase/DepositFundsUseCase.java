package br.com.wallet.application.usecase;

import br.com.wallet.domain.context.Deposit;

public interface DepositFundsUseCase  extends UseCase<Deposit> {
    @Override
    void handle(Deposit deposit);
}
