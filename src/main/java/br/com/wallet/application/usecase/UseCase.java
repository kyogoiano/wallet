package br.com.wallet.application.usecase;

public interface UseCase<T> {
     void handle(T transfer);
}
