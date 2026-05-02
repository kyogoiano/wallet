package br.com.wallet.infrasctructure.persistence;

import br.com.wallet.domain.LedgerEntry;
import br.com.wallet.domain.LedgerType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class LedgerDao {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public LedgerDao(final JdbcTemplate jdbc,
                     final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbc = jdbc;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
     * This might run into GHOST reads during racing conditions
     * @param walletId wallet id
     * @return previous hash
     */
    @Deprecated
    public String findPreviousHash(@NonNull final UUID walletId, @NonNull final  Long sequence) {
        return jdbc.query("""
                    SELECT hash
                    FROM ledger
                    WHERE wallet_id = ? AND sequence = ?
                    LIMIT 1
                """, rs -> rs.next() ? rs.getString("hash") : null, walletId);
    }

    /**
     * Insert ledger record using db hash calculation, this impl let db cal
     * The inner select that pick the previous hash prevents ghost reads on racing conditions
     * Racing Scene: T1 execute updateBalance -> receive sequence = 10.
     * T2 execute updateBalance -> receive sequence = 11.
     * T2 try to findPreviousHash(walletI, 10)
     * Also to adhere to the hash consistency we will calculate hash inside PostgreSQL pgcrypto extension
     * Hash calculations using SHA512 with critical fields
     *
     * @param walletId     wallet id
     * @param amount       money amount
     * @param ledgerType   ledger operation type
     * @param operationId  operation id
     * @param userId       user id
     * @param nextSequence next sequence
     * @param now          operation instant
     */
    public void insertLedger(@NonNull final UUID walletId,
                             @NonNull final BigDecimal amount,
                             @NonNull final LedgerType ledgerType,
                             @NonNull final UUID operationId,
                             @NonNull final UUID userId,
                             @NonNull final Long nextSequence,
                             @NonNull final Instant now) {
        final var params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("walletId", walletId)
                .addValue("amount", amount)
                .addValue("type", ledgerType.name())
                .addValue("operationId", operationId)
                .addValue("userId", userId)
                .addValue("now", now.atOffset(ZoneOffset.UTC))
                .addValue("sequence", nextSequence);
        namedParameterJdbcTemplate.update("""
                            INSERT INTO ledger (
                                id, wallet_id, amount, type, operation_id, user_id, created_at, sequence, previous_hash, hash
                            )
                            WITH prev_data AS (
                                SELECT hash FROM ledger
                                WHERE wallet_id = :walletId AND sequence = (:sequence - 1)
                            )
                            SELECT
                                :id,
                                :walletId,
                                :amount,
                                :type,
                                :operationId,
                                :userId,
                                :now,
                                :sequence,
                                (SELECT hash FROM prev_data),
                                encode(digest(
                                    concat_ws('|',
                                                coalesce((SELECT hash FROM prev_data), 'GENESIS'),
                                                :walletId::text,
                                                to_char(:amount, 'FM99999999999999999.00'),
                                                :type,
                                                :sequence::text,
                                                :operationId::text,
                                                :userId::text,
                                                (extract(epoch from :now) * 1000)::bigint::text
                                    ),
                                    'sha512'
                                ), 'hex')
                        """,params
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
            SELECT wallet_id, amount, type, operation_id, user_id,
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
                UUID.fromString(rs.getString("user_id")),
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
            SELECT wallet_id, amount, type, operation_id, user_id,
                   sequence, hash, previous_hash, created_at
            FROM ledger
            WHERE wallet_id = ?
            ORDER BY sequence ASC
        """, (rs, rowNum) -> new LedgerEntry(
                UUID.fromString(rs.getString("wallet_id")),
                rs.getBigDecimal("amount"),
                LedgerType.valueOf(rs.getString("type")),
                UUID.fromString(rs.getString("operation_id")),
                UUID.fromString(rs.getString("user_id")),
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

    /**
     * Fast hash chain and sequence verification
     * @param walletId wallet id
     * @return corrupted sequences
     */
    public List<Long> findCorruptedEntries(@NonNull final UUID walletId) {
        return jdbc.query("""
            SELECT sequence
            FROM ledger
            WHERE wallet_id = ?
            AND hash <> encode(digest(
                    concat_ws('|',
                        coalesce(previous_hash, 'GENESIS'),
                        wallet_id::text,
                        trim(to_char(amount, '99999999999999990.00')),
                        type,
                        sequence::text,
                        operation_id::text,
                        user_id::text,
                        (extract(epoch from created_at) * 1000)::bigint::text
                    ),
                    'sha512'
                ), 'hex')
            ORDER BY sequence ASC
       """, (rs, rowNum) -> rs.getLong("sequence"), walletId);
    }

    /**
     *  Verify "Broken Chain" (if N-1 line hash is equals to previous_hash on line N)
     *  * Fast Windows function (lead over)
     * @param walletId wallet id
     * @return true if chain is broken
     */
    public Boolean checkChainBroken(@NonNull final UUID walletId) {
        return jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM (
                    SELECT hash,
                           lead(previous_hash) OVER (ORDER BY sequence) as next_entry_prev_hash,
                           sequence
                    FROM ledger
                    WHERE wallet_id = ?
                ) t
                WHERE next_entry_prev_hash IS NOT NULL AND hash <> next_entry_prev_hash
            )
        """, Boolean.class, walletId);
    }
}
