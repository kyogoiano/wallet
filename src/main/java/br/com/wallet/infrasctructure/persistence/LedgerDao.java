package br.com.wallet.infrasctructure.persistence;

import br.com.wallet.domain.LedgerEntry;
import br.com.wallet.domain.LedgerType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class LedgerDao {

    private final JdbcTemplate jdbc;

    public LedgerDao(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get historical balance at a point in time
     * Ordering improves debugging
     * @param walletId wallet id
     * @param createdAt instant
     * @return balance
     */
    public BigDecimal getBalanceAt(@NonNull UUID walletId, @NonNull Instant createdAt) {
        return jdbc.queryForObject("""
                    SELECT COALESCE(SUM(
                                 CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END
                             ), 0)
                    FROM ledger
                    WHERE wallet_id = ?
                    AND created_at <= ?
                """, BigDecimal.class, walletId, createdAt.atOffset(ZoneOffset.UTC));
    }

    /**
     * Find previous hash from a wallet
     * @param walletId wallet id
     * @return previous hash
     */
    public String findPreviousHash(@NonNull final UUID walletId) {
        return jdbc.query("""
                    SELECT hash
                    FROM ledger
                    WHERE wallet_id = ?
                    ORDER BY sequence DESC
                    LIMIT 1
                """, rs -> rs.next() ? rs.getString("hash") : null, walletId);
    }

    /**
     * Insert ledger record
     * @param walletId wallet id
     * @param amount money amount
     * @param ledgerType ledger operation type
     * @param operationId operation id
     * @param nextSequence next sequence
     * @param hash hash input ( used to calculate hash on db)
     * @param now operation instant
     * @param prevHash previous hash
     */
    public void insertLedger(@NonNull final UUID walletId,
                              @NonNull final BigDecimal amount,
                              @NonNull final LedgerType ledgerType,
                              @NonNull final UUID operationId,
                              @NonNull final Long nextSequence,
                              @NonNull final String hash,
                              @NonNull final Instant now,
                              @Nullable final String prevHash) {
        jdbc.update("""
                            INSERT INTO ledger (
                                id, wallet_id, amount, type, operation_id, created_at, sequence, hash, previous_hash
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                walletId,
                amount,
                ledgerType.name(),
                operationId,
                now.atOffset(ZoneOffset.UTC),
                nextSequence,
                hash,
                prevHash
        );
    }


    /**
     * Get all ledger entries from a wallet
     * Notice: This is an O(n) CPU+Memory operation, I kept it here as a back-office operation
     * since I refactored it for performance direct on the validate ledger service
     *
     * @param walletId wallet id
     * @param limit entries limit
     * @return ledger entries
     */
    public @NonNull List<LedgerEntry> getLedgerEntries(@NonNull UUID walletId, @NonNull Integer limit) {
        return jdbc.query("""
            SELECT wallet_id, amount, type, operation_id,
                   sequence, hash, previous_hash, created_at
            FROM ledger
            WHERE wallet_id = ?
            ORDER BY sequence ASC
            LIMIT ?
        """, (rs, rowNum) -> new LedgerEntry(
                UUID.fromString(rs.getString("wallet_id")),
                rs.getBigDecimal("amount"),
                LedgerType.valueOf(rs.getString("type")),
                UUID.fromString(rs.getString("operation_id")),
                rs.getLong("sequence"),
                rs.getString("hash"),
                rs.getString("previous_hash"),
                rs.getTimestamp("created_at").toInstant()
        ), walletId, limit);
    }

    /**
     * Get all ledger entries from a wallet
     * This is required only for full replays (O(n))
     */
    public @NonNull List<LedgerEntry> getLedgerEntries(@NonNull UUID walletId) {
        return jdbc.query("""
            SELECT wallet_id, amount, type, operation_id,
                   sequence, hash, previous_hash, created_at
            FROM ledger
            WHERE wallet_id = ?
            ORDER BY sequence ASC
        """, (rs, rowNum) -> new LedgerEntry(
                UUID.fromString(rs.getString("wallet_id")),
                rs.getBigDecimal("amount"),
                LedgerType.valueOf(rs.getString("type")),
                UUID.fromString(rs.getString("operation_id")),
                rs.getLong("sequence"),
                rs.getString("hash"),
                rs.getString("previous_hash"),
                rs.getTimestamp("created_at").toInstant()
        ), walletId);
    }

    public @Nullable Long validateSequenceContinuity(@NonNull UUID walletId) {

        return jdbc.queryForObject("""
            SELECT COUNT(*) = MAX(sequence) AS valid_sequence
              FROM ledger
              WHERE wallet_id = ?;
        """, Long.class, walletId);
    }
}
