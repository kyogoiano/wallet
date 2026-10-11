package br.com.wallet.goals.internal.persistence;

import br.com.wallet.goals.api.model.FinancialGoal;
import br.com.wallet.goals.api.model.GoalPriority;
import br.com.wallet.goals.api.model.GoalStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FinancialGoalDao {

    private final JdbcTemplate jdbc;

    public FinancialGoalDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public void insert(@NonNull final FinancialGoal goal) {
        Objects.requireNonNull(goal, "goal cannot be null");
        jdbc.update("""
            INSERT INTO goals (id, user_id, wallet_id, target_wallet_id, name, target_amount, target_date, priority, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
                goal.id(),
                goal.userId(),
                goal.walletId(),
                goal.targetWalletId(),
                goal.name(),
                goal.targetAmount(),
                Date.valueOf(goal.targetDate()),
                goal.priority().name(),
                goal.status().name(),
                Timestamp.from(goal.createdAt()),
                Timestamp.from(goal.updatedAt())
        );
    }

    public void update(@NonNull final FinancialGoal goal) {
        Objects.requireNonNull(goal, "goal cannot be null");
        jdbc.update("""
            UPDATE goals
            SET name = ?, target_amount = ?, target_date = ?, priority = ?, updated_at = ?
            WHERE id = ?
        """,
                goal.name(),
                goal.targetAmount(),
                Date.valueOf(goal.targetDate()),
                goal.priority().name(),
                Timestamp.from(Instant.now()),
                goal.id()
        );
    }

    public void updateStatus(@NonNull final UUID id, @NonNull final GoalStatus status) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        jdbc.update("""
            UPDATE goals
            SET status = ?, updated_at = ?
            WHERE id = ?
        """, status.name(), Timestamp.from(Instant.now()), id);
    }

    public Optional<FinancialGoal> findById(@NonNull final UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        List<FinancialGoal> results = jdbc.query("""
            SELECT id, user_id, wallet_id, target_wallet_id, name, target_amount, target_date, priority, status, created_at, updated_at
            FROM goals
            WHERE id = ?
        """, (rs, rowNum) -> mapRow(rs), id);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<FinancialGoal> findByWalletId(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return jdbc.query("""
            SELECT id, user_id, wallet_id, target_wallet_id, name, target_amount, target_date, priority, status, created_at, updated_at
            FROM goals
            WHERE wallet_id = ?
            ORDER BY created_at DESC
        """, (rs, rowNum) -> mapRow(rs), walletId);
    }

    public List<FinancialGoal> findByWalletIdAndStatus(@NonNull final UUID walletId, @NonNull final GoalStatus status) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        return jdbc.query("""
            SELECT id, user_id, wallet_id, target_wallet_id, name, target_amount, target_date, priority, status, created_at, updated_at
            FROM goals
            WHERE wallet_id = ? AND status = ?
            ORDER BY created_at DESC
        """, (rs, rowNum) -> mapRow(rs), walletId, status.name());
    }

    private FinancialGoal mapRow(ResultSet rs) throws SQLException {
        UUID targetWalletId = rs.getObject("target_wallet_id", UUID.class);
        return new FinancialGoal(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getObject("wallet_id", UUID.class),
                targetWalletId,
                rs.getString("name"),
                rs.getBigDecimal("target_amount"),
                rs.getDate("target_date").toLocalDate(),
                GoalPriority.valueOf(rs.getString("priority")),
                GoalStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
