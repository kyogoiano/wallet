package br.com.wallet.infrasctructure.persistence;

import br.com.wallet.domain.Account;
import br.com.wallet.domain.AccountBalance;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class AccountDao {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    public AccountDao(final JdbcTemplate jdbc, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbc = jdbc;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }


    /**
     * Insert new accounts, always starts with 0!
     *
     * @param walletId wallet id
     * @param userId user id
     */
    public void insertAccount(final UUID walletId, final UUID userId) {
        jdbc.update("""
            INSERT INTO accounts (id, balance, user_id, version)
            VALUES (?, ?, ?, 0)
        """, walletId, BigDecimal.ZERO, userId);
    }

    public Optional<Account> findAccount(@NonNull final UUID walletId) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT * from accounts WHERE id = ?
                """, Account.class, walletId));
    }

    public Optional<UUID> findUserId(@NonNull final UUID walletId) {
        return Optional.ofNullable(jdbc.queryForObject("""
                SELECT user_id from accounts WHERE id = ?
                """, UUID.class, walletId));
    }


    public List<@NonNull Account> listAccounts(@NonNull final Integer limit, final @NonNull Integer offset) {
        final var params = new MapSqlParameterSource()
                .addValue("limit", limit)
                .addValue("offset", offset);
        return namedParameterJdbcTemplate.query("""
                 SELECT id, balance, version, user_id, created_at FROM accounts
                 ORDER BY created_at ASC
                 LIMIT :limit OFFSET :offset
        """, params, (rs, rowNum) -> new Account(
               rs.getObject("id", UUID.class),
               rs.getBigDecimal("balance"),
               rs.getLong("version"),
               rs.getObject("user_id", UUID.class),
               rs.getTimestamp("created_at").toInstant()
        ));
    }

    public Optional<BigDecimal> findWalletBalance(@NonNull final UUID walletId) {
           return jdbc.query("""
                SELECT balance FROM accounts WHERE id = ?
                """, rs -> {
                    if (rs.next()) {
                        return Optional.of(rs.getBigDecimal("balance"));
                    }
                    return Optional.empty();
        }, walletId);
    }

    public Optional<Map.Entry<@NonNull UUID, @NonNull BigDecimal>> findWalletBalanceForUpdate(@NonNull final UUID walletId) {
        return jdbc.query("""
                SELECT user_id, balance FROM accounts WHERE id = ? FOR UPDATE
                """, rs -> {
            if (rs.next()) {
                return Optional.of(Map.entry(rs.getObject("user_id", UUID.class), rs.getBigDecimal("balance")));
            }
            return Optional.empty();
        }, walletId);
    }

    @Deprecated
    /*
     * this method can break sequence on race conditions
     */
    public Long nextAccountSequence(@NonNull final UUID walletId) {
        return jdbc.queryForObject("""
                    UPDATE accounts
                    SET last_sequence = last_sequence + 1
                    WHERE id = ?
                    RETURNING last_sequence
                """, Long.class, walletId);
    }

    public Long updateBalance(@NonNull final UUID walletId, @NonNull final BigDecimal amount) {
        return jdbc.queryForObject("""
            UPDATE accounts
            SET balance = balance + ?, version = version + 1, last_sequence = last_sequence + 1
            WHERE id = ?
            RETURNING last_sequence
        """, Long.class, amount, walletId);
    }

    /**
     * this locks the balance in both wallets (account)
     * WARN: DB respects that order during locking.
     * @param ordered list of wallets ids
     * @return id/user_id and balance map
     */
    public Map<UUID, AccountBalance> getBalancesFromWallets(final List<@NonNull UUID> ordered) {
        return jdbc.query("""
            SELECT id, user_id, balance
            FROM accounts
            WHERE id IN (?, ?)
            FOR UPDATE
        """, rs -> {
            final Map<UUID, AccountBalance> map = new HashMap<>();
            while (rs.next()) {
                map.put(
                        rs.getObject("id", UUID.class),
                        new AccountBalance(rs.getObject("user_id", UUID.class), rs.getBigDecimal("balance"))
                );
            }
            return map;
        }, ordered.get(0), ordered.get(1));
    }

}
