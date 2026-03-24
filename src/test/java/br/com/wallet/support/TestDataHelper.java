package br.com.wallet.support;

import br.com.wallet.infrasctructure.outbox.OutboxStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Component
public class TestDataHelper {
    @Autowired
    JdbcTemplate jdbc;

    public UUID createWallet(BigDecimal balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO accounts (id, balance, version)
            VALUES (?, ?, 0)
        """, id, balance);
        return id;
    }


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

    public void tamperPreviousHash(UUID walletId, long sequence, String fakeHash) {
        jdbc.update("""
            UPDATE ledger
            SET previous_hash = ?
            WHERE wallet_id = ?
            AND sequence = ?
        """, fakeHash, walletId, sequence);
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
            WHERE operation_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """, UUID.class, operationId);
    }
}
