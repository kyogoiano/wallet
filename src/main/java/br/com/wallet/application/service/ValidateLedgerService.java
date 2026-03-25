package br.com.wallet.application.service;

import br.com.wallet.application.usecase.ValidateLedgerUseCase;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.LedgerValidationResult;
import br.com.wallet.infrasctructure.persistence.LedgerDao;
import br.com.wallet.util.HashUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class ValidateLedgerService implements ValidateLedgerUseCase {

    private static final Logger log = LoggerFactory.getLogger(ValidateLedgerService.class);

    private final JdbcTemplate jdbc;

    /**
     * Validate Ledger Service
     * As this service has in important optimization it includes direct access to jdbc template instead of delivering to a dao
     * @param jdbc optimized dao operations
     */
    public ValidateLedgerService(final  JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    @Override
    public LedgerValidationResult execute(@NonNull final UUID walletId) {
        return  this.validLedgerEntries(walletId);
    }

    /**
     * Optimized Validation ledger entries from a wallet
     * Streaming (Optimized memory)
     * Deterministic operations
     * Tamper-proof
     * @param walletId wallet id
     * @return ledger validation result
     */
    private @NonNull LedgerValidationResult validLedgerEntries(@NonNull UUID walletId) {

        return jdbc.query("""
                SELECT amount, type, operation_id,
                       sequence, hash, previous_hash, created_at
                FROM ledger
                WHERE wallet_id = ?
                ORDER BY sequence ASC
            """, rs -> {

            String expectedPrev = null;
            long expectedSequence = 1L;
            long count = 0;

            while (rs.next()) {

                final var actualPrev = rs.getString("previous_hash");
                final var hash = rs.getString("hash");
                final var amount = rs.getBigDecimal("amount");
                final var ledgerType = LedgerType.valueOf(rs.getString("type"));
                final var sequence = rs.getLong("sequence");
                final var operationId = rs.getObject("operation_id", UUID.class);
                final var createdAt = rs.getTimestamp("created_at").toInstant();

                // 🔢 sequence validation
                if (sequence != expectedSequence) {
                    return new LedgerValidationResult(
                            false, count, "Invalid sequence at " + sequence + ", expected " + expectedSequence
                    );
                }

                // 🔗 previous hash validation
                if (!Objects.equals(expectedPrev, actualPrev)) {
                    return new LedgerValidationResult(
                            false, count, "Broken chain at sequence " + sequence
                    );
                }

                // 🔐 recompute hash
                final var recomputed = HashUtils.calculateHash(
                        expectedPrev,
                        walletId,
                        amount,
                        ledgerType,
                        sequence,
                        operationId,
                        createdAt
                );

                if (!hash.equals(recomputed)) {
                    return new LedgerValidationResult(
                            false, count, "Invalid hash at sequence " + sequence
                    );
                }

                expectedPrev = hash;
                expectedSequence++;
                count++;
            }

            log.info("Ledger entries are valid! walletId={}, entries size={}", walletId, count);

            return new LedgerValidationResult(true, count, null);
        }, walletId);
    }
}
