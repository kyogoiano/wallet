package br.com.wallet.util;

import br.com.wallet.domain.LedgerType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class HashUtils {

    /**
     * Build Ledger hash in a deterministic way
     * @param previousHash previous hash
     * @param walletId wallet id
     * @param amount money amount
     * @param ledgerType ledger type
     * @param sequence sequence
     * @param operationId operation id
     * @param now current instant
     * @return ledger hash input
     */
    public static String buildLedgerHashInput(@Nullable final String previousHash,
                                  @NonNull final UUID walletId,
                                  @NonNull final BigDecimal amount,
                                  @NonNull final LedgerType ledgerType,
                                  @NonNull final Long sequence,
                                  @NonNull final UUID operationId,
                                  @NonNull final Instant now) {
        return String.join("|",
                previousHash == null ? "GENESIS" : previousHash,
                walletId.toString(),
                amount.toPlainString(),
                ledgerType.name(),
                String.valueOf(sequence),
                operationId.toString(),
                String.valueOf(now.toEpochMilli())
        );
    }
}
