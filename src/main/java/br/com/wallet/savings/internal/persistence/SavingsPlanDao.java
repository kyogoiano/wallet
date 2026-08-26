package br.com.wallet.savings.internal.persistence;

import br.com.wallet.savings.internal.domain.SavingsPlan;
import br.com.wallet.savings.internal.domain.SavingsRule;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SavingsPlanDao {

    private final JdbcTemplate jdbc;
    private final SavingsRuleDao savingsRuleDao;

    public SavingsPlanDao(@NonNull final JdbcTemplate jdbc, @NonNull final SavingsRuleDao savingsRuleDao) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.savingsRuleDao = Objects.requireNonNull(savingsRuleDao, "savingsRuleDao cannot be null");
    }

    public void insert(@NonNull final SavingsPlan plan) {
        Objects.requireNonNull(plan, "plan cannot be null");
        jdbc.update("""
            INSERT INTO savings_plans (id, source_wallet_id, target_wallet_id, minimum_retained_balance, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, plan.id(), plan.sourceWalletId(), plan.targetWalletId(), plan.minimumRetainedBalance(), plan.status(),
                java.sql.Timestamp.from(plan.createdAt()), java.sql.Timestamp.from(plan.updatedAt()));

        for (SavingsRule rule : plan.rules()) {
            savingsRuleDao.insert(rule);
        }
    }

    public Optional<SavingsPlan> findById(@NonNull final UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        return jdbc.query("""
            SELECT id, source_wallet_id, target_wallet_id, minimum_retained_balance, status, created_at, updated_at
            FROM savings_plans
            WHERE id = ?
        """, rs -> {
            if (rs.next()) {
                UUID planId = rs.getObject("id", UUID.class);
                List<SavingsRule> rules = savingsRuleDao.findByPlanId(planId);
                return Optional.of(new SavingsPlan(
                        planId,
                        rs.getObject("source_wallet_id", UUID.class),
                        rs.getObject("target_wallet_id", UUID.class),
                        rs.getBigDecimal("minimum_retained_balance"),
                        rs.getString("status"),
                        rules,
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ));
            }
            return Optional.empty();
        }, id);
    }

    public List<SavingsPlan> findBySourceWalletId(@NonNull final UUID sourceWalletId) {
        Objects.requireNonNull(sourceWalletId, "sourceWalletId cannot be null");
        return jdbc.query("""
            SELECT id, source_wallet_id, target_wallet_id, minimum_retained_balance, status, created_at, updated_at
            FROM savings_plans
            WHERE source_wallet_id = ?
            ORDER BY created_at DESC
        """, (rs, rowNum) -> {
            UUID planId = rs.getObject("id", UUID.class);
            List<SavingsRule> rules = savingsRuleDao.findByPlanId(planId);
            return new SavingsPlan(
                    planId,
                    rs.getObject("source_wallet_id", UUID.class),
                    rs.getObject("target_wallet_id", UUID.class),
                    rs.getBigDecimal("minimum_retained_balance"),
                    rs.getString("status"),
                    rules,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }, sourceWalletId);
    }

    public List<SavingsPlan> findActiveBySourceWalletId(@NonNull final UUID sourceWalletId) {
        Objects.requireNonNull(sourceWalletId, "sourceWalletId cannot be null");
        return jdbc.query("""
            SELECT id, source_wallet_id, target_wallet_id, minimum_retained_balance, status, created_at, updated_at
            FROM savings_plans
            WHERE source_wallet_id = ? AND status = 'ACTIVE'
            ORDER BY created_at DESC
        """, (rs, rowNum) -> {
            UUID planId = rs.getObject("id", UUID.class);
            List<SavingsRule> rules = savingsRuleDao.findActiveByPlanId(planId);
            return new SavingsPlan(
                    planId,
                    rs.getObject("source_wallet_id", UUID.class),
                    rs.getObject("target_wallet_id", UUID.class),
                    rs.getBigDecimal("minimum_retained_balance"),
                    rs.getString("status"),
                    rules,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }, sourceWalletId);
    }

    public void updateStatus(@NonNull final UUID id, @NonNull final String status) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        jdbc.update("""
            UPDATE savings_plans
            SET status = ?, updated_at = NOW()
            WHERE id = ?
        """, status, id);
    }

    public void touchUpdatedAt(@NonNull final UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        jdbc.update("UPDATE savings_plans SET updated_at = NOW() WHERE id = ?", id);
    }

    public void delete(@NonNull final UUID id) {
        Objects.requireNonNull(id, "id cannot be null");
        jdbc.update("DELETE FROM savings_plans WHERE id = ?", id);
    }
}
