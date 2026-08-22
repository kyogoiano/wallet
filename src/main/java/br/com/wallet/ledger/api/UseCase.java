package br.com.wallet.ledger.api;

public interface UseCase<T> {
     void handle(T transfer);
}
