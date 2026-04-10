package br.com.wallet.infrasctructure.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class AccountDao {

    private final JdbcTemplate jdbc;

    public AccountDao(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }


    /**
     * Insert new accounts, always starts with 0!
     * @param walletId wallet id
     */
    public void insertAccount(final UUID walletId) {
        jdbc.update("""
            INSERT INTO accounts (id, balance, version)
            VALUES (?, ?, 0)
        """, walletId, BigDecimal.ZERO);
    }

    public Optional<BigDecimal> findWalletBalance(@NonNull UUID walletId) {
        return jdbc.query("""
                SELECT balance FROM accounts WHERE id = ?
                """, rs -> {
                    if (rs.next()) {
                        return Optional.of(rs.getBigDecimal("balance"));
                    }
                    return Optional.empty();
        }, walletId);
    }

    public Optional<BigDecimal> findWalletBalanceForUpdate(@NonNull UUID walletId) {
        return jdbc.query("""
                SELECT balance FROM accounts WHERE id = ? FOR UPDATE
                """, rs -> {
            if (rs.next()) {
                return Optional.of(rs.getBigDecimal("balance"));
            }
            return Optional.empty();
        }, walletId);
    }

    @Deprecated
    /*
     * this method can break sequence on race conditions
     */
    public Long nextAccountSequence(@NonNull UUID walletId) {
        return jdbc.queryForObject("""
                    UPDATE accounts
                    SET last_sequence = last_sequence + 1
                    WHERE id = ?
                    RETURNING last_sequence
                """, Long.class, walletId);
    }

    public Long updateBalance(@NonNull UUID accountId, @NonNull BigDecimal amount) {
        return jdbc.queryForObject("""
            UPDATE accounts
            SET balance = balance + ?, version = version + 1, last_sequence = last_sequence + 1
            WHERE id = ?
            RETURNING last_sequence
        """, Long.class, amount, accountId);
    }

    /**
     * this locks the balance in both wallets (account)
     * WARN: DB respects that order during locking.
     * @param ordered list of wallets ids
     * @return id and balance map
     */
    public Map<UUID, BigDecimal> getBalancesFromWallets(List<@NonNull UUID> ordered) {
        return jdbc.query("""
            SELECT id, balance
            FROM accounts
            WHERE id IN (?, ?)
            FOR UPDATE
        """, rs -> {
            Map<UUID, BigDecimal> map = new HashMap<>();
            while (rs.next()) {
                map.put(
                        rs.getObject("id", UUID.class),
                        rs.getBigDecimal("balance")
                );
            }
            return map;
        }, ordered.get(0), ordered.get(1));
    }

}
