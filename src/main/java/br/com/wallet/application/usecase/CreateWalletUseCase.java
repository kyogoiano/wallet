package br.com.wallet.application.usecase;


import java.math.BigDecimal;
import java.util.UUID;

public interface CreateWalletUseCase {
    UUID execute(BigDecimal initialBalance, UUID operationId);
    UUID execute();
}