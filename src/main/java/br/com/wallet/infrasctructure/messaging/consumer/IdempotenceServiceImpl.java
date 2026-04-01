package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.infrasctructure.persistence.WalletOperationsDao;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IdempotenceServiceImpl implements IdempotencyService {

    private final WalletOperationsDao walletOperationsDao;

    public IdempotenceServiceImpl(WalletOperationsDao walletOperationsDao) {
        this.walletOperationsDao = walletOperationsDao;
    }

    @Override
    public boolean isProcessed(UUID operationId) {
        return walletOperationsDao.operationExists(operationId);
    }

    @Override
    public boolean markProcessed(UUID operationId) {
        return walletOperationsDao.tryRegister(operationId);
    }
}
