package br.com.wallet.savings.internal.persistence;

import br.com.wallet.savings.api.model.SavingsRuleType;
import br.com.wallet.savings.internal.domain.SavingsRule;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class SavingsRuleDao {

    private final JdbcTemplate jdbc;

    private final RowMapper<SavingsRule> rowMapper = (rs, rowNum) -> new SavingsRule(
            rs.getObject("id", UUID.class),
            rs.getObject("plan_id", UUID.class),
            SavingsRuleType.valueOf(rs.getString("rule_type")),
            rs.getBigDecimal("step_amount"),
            rs.getBigDecimal("percentage_rate"),
            rs.getBigDecimal("ceiling_threshold"),
            rs.getBoolean("is_active")
    );

    public SavingsRuleDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public void insert(@NonNull final SavingsRule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        jdbc.update("""
            INSERT INTO savings_rules (id, plan_id, rule_type, step_amount, percentage_rate, ceiling_threshold, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, rule.id(), rule.planId(), rule.ruleType().name(), rule.stepAmount(), rule.percentageRate(), rule.ceilingThreshold(), rule.isActive());
    }

    public List<SavingsRule> findByPlanId(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        return jdbc.query("""
            SELECT id, plan_id, rule_type, step_amount, percentage_rate, ceiling_threshold, is_active
            FROM savings_rules
            WHERE plan_id = ?
            ORDER BY created_at ASC
        """, rowMapper, planId);
    }

    public List<SavingsRule> findActiveByPlanId(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        return jdbc.query("""
            SELECT id, plan_id, rule_type, step_amount, percentage_rate, ceiling_threshold, is_active
            FROM savings_rules
            WHERE plan_id = ? AND is_active = TRUE
            ORDER BY created_at ASC
        """, rowMapper, planId);
    }

    public void deleteByPlanId(@NonNull final UUID planId) {
        Objects.requireNonNull(planId, "planId cannot be null");
        jdbc.update("DELETE FROM savings_rules WHERE plan_id = ?", planId);
    }
}
