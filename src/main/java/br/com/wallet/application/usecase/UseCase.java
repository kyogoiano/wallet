package br.com.wallet.application.usecase;

import br.com.wallet.exceptions.BusinessException;

public interface UseCase<T> {
     void handle(T transfer) throws BusinessException;
}
