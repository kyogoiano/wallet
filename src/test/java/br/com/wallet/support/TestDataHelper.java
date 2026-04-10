package br.com.wallet.support;

import br.com.wallet.domain.LedgerEntry;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.infrasctructure.outbox.OutboxStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Component
public class TestDataHelper {
    @Autowired
    JdbcTemplate jdbc;


    public void assertBalance(UUID walletId, BigDecimal expected) {
        BigDecimal balance = jdbc.queryForObject("""
            SELECT balance FROM accounts WHERE id = ?
        """, BigDecimal.class, walletId);

        assertThat(balance).isEqualByComparingTo(expected);
    }

    public void assertWalletExists(UUID walletId) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM accounts WHERE id = ?
        """, Integer.class, walletId);

        assertThat(count).isEqualTo(1);
    }

    public void tamperFirstLedgerEntry(UUID walletId, BigDecimal newAmount) {
        jdbc.update("""
            UPDATE ledger
            SET amount = ?
            WHERE wallet_id = ?
            AND sequence = (
                SELECT MIN(sequence)
                FROM ledger
                WHERE wallet_id = ?
            )
        """, newAmount, walletId, walletId);
    }

    public void tamperSequence(UUID walletId, long originalSequence, long newSequence) {
        jdbc.update("""
            UPDATE ledger
            SET sequence = ?
            WHERE wallet_id = ?
            AND sequence = ?
        """, newSequence, walletId, originalSequence);
    }

    public void tamperPreviousHash(UUID walletId, long sequence, String fakeHash, UUID opId) {
        jdbc.update("""
            UPDATE ledger
            SET previous_hash = ?
            WHERE wallet_id = ?
            AND sequence = ?
            AND operation_id = ?
        """, fakeHash, walletId, sequence, opId);
    }

    /**
     * This method breaks the chain link between N and N+1.
     * It updates entry N+1's previous_hash and recalculates its hash so it is still "consistent",
     * but no longer matches entry N's hash.
     */
    public void tamperConsistentChainBreak(UUID walletId, long sequence) {
        // First get the entry to re-hash it correctly with a fake previous hash
        jdbc.update("""
            UPDATE ledger
            SET previous_hash = 'broken_link_consistent_lie',
                hash = encode(digest(
                    concat_ws('|',
                        'broken_link_consistent_lie',
                        wallet_id::text,
                        trim(to_char(amount, '99999999999999990.00')),
                        type,
                        sequence::text,
                        operation_id::text,
                        (extract(epoch from created_at) * 1000)::bigint::text
                    ),
                    'sha512'
                ), 'hex')
            WHERE wallet_id = ? AND sequence = ?
        """, walletId, sequence);
    }

    public Integer countProcessedOutbox(UUID operationId) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM outbox
            WHERE event_type = 'TRANSFER_COMPLETED'
            AND aggregate_id = ?
        """, Integer.class, operationId);
    }


    public void corruptOutboxPayload(UUID eventId) {
        jdbc.update("""
            UPDATE outbox
            SET payload = '{"invalid": true}'
            WHERE id = ?
        """, eventId);
    }


    public OutboxStatus getStatus(UUID eventId) {
        return jdbc.queryForObject("""
            SELECT status FROM outbox
            WHERE  id = ?
        """, OutboxStatus.class, eventId);
    }

    public Integer getRetryCount(UUID eventId) {
        return jdbc.queryForObject("""
            SELECT retry_count FROM outbox
            WHERE  id = ?
        """, Integer.class, eventId);
    }

    public Instant getProcessedAt(UUID eventId) {
        return jdbc.queryForObject("""
            SELECT processed_at FROM outbox
            WHERE  id = ?
        """, Instant.class, eventId);
    }

    public UUID getOutboxIdByOperation(UUID operationId) {
        return jdbc.queryForObject("""
            SELECT id
            FROM outbox
            WHERE aggregate_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """, UUID.class, operationId);
    }

    public void forceRetryNow(UUID eventId) {
        jdbc.update("""
        UPDATE outbox
        SET next_retry_at = NOW()
        WHERE id = ?
    """, eventId);
    }

    public List<LedgerEntry> getLedgerEntries(UUID walletId) {
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

    public void tamperAmount(UUID walletId, Long sequence, BigDecimal amount, UUID opId) {
        jdbc.update("""
            UPDATE ledger
            SET amount = ?
            WHERE wallet_id = ?
            AND sequence = ?
            AND operation_id = ?
        """, amount, walletId, sequence, opId);
    }

    public Long getOutboxEventsByOperation(UUID opId) {
        return jdbc.queryForObject("""
            SELECT  COUNT(aggregate_id)
            FROM outbox
            WHERE aggregate_id = ?
            GROUP BY aggregate_id, created_at
            ORDER BY created_at DESC
        """, Long.class, opId);
    }
}
