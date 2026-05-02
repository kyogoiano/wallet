package br.com.wallet.util;

import br.com.wallet.domain.LedgerType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;


public class HashUtils {

    private static final MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String calculateHash(
            @Nullable String previousHash,
            @NonNull UUID walletId,
            @NonNull BigDecimal amount,
            @NonNull LedgerType ledgerType,
            @NonNull Long sequence,
            @NonNull UUID operationId,
            @NonNull UUID userId,
            @NonNull Instant createdAt
    ) {
        final var hashInput = HashUtils.buildLedgerHashInput(previousHash, walletId, amount, ledgerType, sequence, operationId, userId, createdAt);

        final byte[] hashBytes = digest.digest(hashInput.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    /**
     * Build Ledger hash in a deterministic way
     * NOTE: made public for unit tests
     * @param previousHash previous hash
     * @param walletId wallet id
     * @param amount money amount
     * @param ledgerType ledger type
     * @param sequence sequence
     * @param operationId operation id
     * @param userId user id
     * @param createdAt current instant
     * @return ledger hash input
     */
    public static String buildLedgerHashInput(@Nullable final String previousHash,
                                  @NonNull final UUID walletId,
                                  @NonNull final BigDecimal amount,
                                  @NonNull final LedgerType ledgerType,
                                  @NonNull final Long sequence,
                                  @NonNull final UUID operationId,
                                  @NonNull final UUID userId,
                                  @NonNull final Instant createdAt) {
        final var normalizedAmount = amount
                .setScale(2, RoundingMode.UNNECESSARY)
                .toPlainString();

        return String.join("|",
                previousHash == null ? "GENESIS" : previousHash,
                walletId.toString(),
                normalizedAmount,
                ledgerType.name(),
                String.valueOf(sequence),
                operationId.toString(),
                String.valueOf(createdAt.toEpochMilli())
        );
    }
}
