package br.com.wallet.wallet.api;

public interface UseCase<T> {
     void handle(T transfer);
}
