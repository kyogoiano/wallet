package br.com.wallet.ledger.api;

import br.com.wallet.ledger.api.context.Transfer;

/**
 * @author Leandro
 * 🧱 3. Business Rules
 *  **order matters!:
 * ___________________________________
 * 1- wallets must not be eguals
 * 2- amount > 0
 * 3- lock on both accounts on this order (min(wallet_id), max(wallet_id))
 * 4- validate balance
 * 5- update accounts
 * 6- insert on ledger (2 entries)
 * 7- insert outbox (for now is optional)
 * 8- commit
 * -----------------------------------
 */
public interface TransferFundsUseCase extends UseCase<Transfer> {
    @Override
    void handle(Transfer transfer);
}
