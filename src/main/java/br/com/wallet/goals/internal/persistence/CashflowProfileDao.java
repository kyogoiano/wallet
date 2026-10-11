package br.com.wallet.goals.internal.persistence;

import br.com.wallet.goals.api.model.CashflowProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CashflowProfileDao {

    private final JdbcTemplate jdbc;

    public CashflowProfileDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public void upsert(@NonNull final CashflowProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        jdbc.update("""
            INSERT INTO cashflow_profiles (id, user_id, wallet_id, monthly_income, monthly_committed_expenses, minimum_safety_buffer, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (wallet_id) DO UPDATE SET
                monthly_income = EXCLUDED.monthly_income,
                monthly_committed_expenses = EXCLUDED.monthly_committed_expenses,
                minimum_safety_buffer = EXCLUDED.minimum_safety_buffer,
                updated_at = EXCLUDED.updated_at
        """,
                profile.id(),
                profile.userId(),
                profile.walletId(),
                profile.monthlyIncome(),
                profile.monthlyCommittedExpenses(),
                profile.minimumSafetyBuffer(),
                Timestamp.from(profile.updatedAt())
        );
    }

    public Optional<CashflowProfile> findByWalletId(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        List<CashflowProfile> results = jdbc.query("""
            SELECT id, user_id, wallet_id, monthly_income, monthly_committed_expenses, minimum_safety_buffer, updated_at
            FROM cashflow_profiles
            WHERE wallet_id = ?
        """, (rs, rowNum) -> mapRow(rs), walletId);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private CashflowProfile mapRow(ResultSet rs) throws SQLException {
        return new CashflowProfile(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("wallet_id", UUID.class),
                rs.getBigDecimal("monthly_income"),
                rs.getBigDecimal("monthly_committed_expenses"),
                rs.getBigDecimal("minimum_safety_buffer"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
