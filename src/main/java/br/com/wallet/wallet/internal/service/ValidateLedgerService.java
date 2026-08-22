package br.com.wallet.wallet.internal.service;

import br.com.wallet.core.tracing.Traceable;
import br.com.wallet.wallet.api.ValidateLedgerUseCase;
import br.com.wallet.wallet.api.domain.LedgerValidationResult;
import br.com.wallet.wallet.internal.persistence.LedgerDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ValidateLedgerService implements ValidateLedgerUseCase {

    private static final Logger log = LoggerFactory.getLogger(ValidateLedgerService.class);

    private final LedgerDao ledgerDao;


    /**
     * Validate Ledger Service
     * As this service has in important optimization it includes direct access to jdbc template instead of delivering to a dao
     * @param ledgerDao optimized dao operations
     */
    public ValidateLedgerService(final LedgerDao ledgerDao) {
        this.ledgerDao = ledgerDao;
    }

    @Traceable("wallet.validateLedger")
    @Override
    public LedgerValidationResult execute(@NonNull final UUID walletId) {
        return  this.validLedgerEntries(walletId);
    }

    /**
     * Optimized Validation ledger entries from a wallet
     * * WARNING: even optimized, this still an O(n) complexity, as it stills verify each byte, but the latency during the operation is greatly reduced
     * * Prior optimization (stream based): Buffer Pool -> Driver JDBC -> JVM -> Hash.
     * * After optimization: Buffer Pool -> DB Hash
     * ** Now we have a change on data movement complexity  from O(n) to O(k), where k is the number of invalid registries
     * Deterministic operations
     * Tamper-proof
     * @param walletId wallet id
     * @return ledger validation result
     */
    private @NonNull LedgerValidationResult validLedgerEntries(@NonNull UUID walletId) {

        final var corruptedData = ledgerDao.findCorruptedEntries(walletId);

        if (!corruptedData.isEmpty()) {
            log.error("Hash mismatch at sequences: {}", corruptedData);
            return new LedgerValidationResult(false, corruptedData.size(), "Hash mismatch at sequences: " + corruptedData);
        }

        final var chainBroken = ledgerDao.checkChainBroken(walletId);

        if (Boolean.TRUE.equals(chainBroken)) {
            log.error("Chain link broken: previous_hash mismatch!");
            return new LedgerValidationResult(false, 0, "Chain link broken: previous_hash mismatch");
        }

        log.info("Ledger validation succeeds!");
        return new LedgerValidationResult(true, 0, "Ledger valid");
    }

}
