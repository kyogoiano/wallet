package br.com.wallet.ledger.internal.persistence;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountBalance;
import br.com.wallet.ledger.api.domain.AccountStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class AccountDao {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final RowMapper<Account> accountRowMapper = (rs, rowNum) -> new Account(
            rs.getObject("id", UUID.class),
            rs.getBigDecimal("balance"),
            rs.getLong("version"),
            rs.getObject("user_id", UUID.class),
            AccountStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("blocked_at") != null ? rs.getTimestamp("blocked_at").toInstant() : null,
            rs.getString("blocked_reason"),
            rs.getTimestamp("created_at").toInstant()
    );

    public AccountDao(final JdbcTemplate jdbc, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.namedParameterJdbcTemplate = Objects.requireNonNull(namedParameterJdbcTemplate, "namedParameterJdbcTemplate cannot be null");
    }

    /**
     * Insert new accounts, always starts with 0!
     *
     * @param walletId wallet id
     * @param userId user id
     */
    public void insertAccount(final UUID walletId, final UUID userId) {
        jdbc.update("""
            INSERT INTO accounts (id, balance, user_id, status, version)
            VALUES (?, ?, ?, 'ACTIVE', 0)
        """, walletId, BigDecimal.ZERO, userId);
    }

    public Optional<Account> findAccount(@NonNull final UUID walletId) {
        return jdbc.query("""
                SELECT id, balance, version, user_id, status, blocked_at, blocked_reason, created_at
                FROM accounts WHERE id = ?
                """, rs -> {
            if (rs.next()) {
                return Optional.of(accountRowMapper.mapRow(rs, 1));
            }
            return Optional.empty();
        }, walletId);
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
                 SELECT id, balance, version, user_id, status, blocked_at, blocked_reason, created_at
                 FROM accounts
                 ORDER BY created_at ASC
                 LIMIT :limit OFFSET :offset
        """, params, accountRowMapper);
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

    public Optional<AccountBalance> findWalletBalanceForUpdate(@NonNull final UUID walletId) {
        return jdbc.query("""
                SELECT id, user_id, balance, status, blocked_reason FROM accounts WHERE id = ? FOR UPDATE
                """, rs -> {
            if (rs.next()) {
                AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
                if (status != AccountStatus.ACTIVE) {
                    throw new AccountBlockedException(walletId, rs.getString("blocked_reason"));
                }
                return Optional.of(new AccountBalance(rs.getObject("user_id", UUID.class), rs.getBigDecimal("balance"), status));
            }
            return Optional.empty();
        }, walletId);
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
     * Locks the balance in both wallets (accounts) in deterministic order and validates active status.
     * @param ordered list of wallet ids
     * @return id/user_id and balance map
     */
    public Map<UUID, AccountBalance> getBalancesFromWallets(final List<@NonNull UUID> ordered) {
        return jdbc.query("""
            SELECT id, user_id, balance, status, blocked_reason
            FROM accounts
            WHERE id IN (?, ?)
            FOR UPDATE
        """, rs -> {
            final Map<UUID, AccountBalance> map = new HashMap<>();
            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                AccountStatus status = AccountStatus.valueOf(rs.getString("status"));
                if (status != AccountStatus.ACTIVE) {
                    throw new AccountBlockedException(id, rs.getString("blocked_reason"));
                }
                map.put(
                        id,
                        new AccountBalance(rs.getObject("user_id", UUID.class), rs.getBigDecimal("balance"), status)
                );
            }
            return map;
        }, ordered.get(0), ordered.get(1));
    }

    public void blockAccount(@NonNull final UUID walletId, @NonNull final String reason) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        jdbc.update("""
            UPDATE accounts
            SET status = 'BLOCKED', blocked_at = NOW(), blocked_reason = ?
            WHERE id = ?
        """, reason, walletId);
    }

    public void blockAccountByUserId(@NonNull final UUID userId, @NonNull final String reason) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        jdbc.update("""
            UPDATE accounts
            SET status = 'BLOCKED', blocked_at = NOW(), blocked_reason = ?
            WHERE user_id = ?
        """, reason, userId);
    }

    public void unblockAccount(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        jdbc.update("""
            UPDATE accounts
            SET status = 'ACTIVE', blocked_at = NULL, blocked_reason = NULL
            WHERE id = ?
        """, walletId);
    }

    public void unblockAccountByUserId(@NonNull final UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        jdbc.update("""
            UPDATE accounts
            SET status = 'ACTIVE', blocked_at = NULL, blocked_reason = NULL
            WHERE user_id = ?
        """, userId);
    }

    public Optional<AccountStatus> findAccountStatus(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return jdbc.query("""
            SELECT status FROM accounts WHERE id = ?
        """, rs -> {
            if (rs.next()) {
                return Optional.of(AccountStatus.valueOf(rs.getString("status")));
            }
            return Optional.empty();
        }, walletId);
    }

    public Optional<AccountStatus> findAccountStatusByUserId(@NonNull final UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        return jdbc.query("""
            SELECT status FROM accounts WHERE user_id = ? LIMIT 1
        """, rs -> {
            if (rs.next()) {
                return Optional.of(AccountStatus.valueOf(rs.getString("status")));
            }
            return Optional.empty();
        }, userId);
    }
}
