package br.com.wallet.savings.internal.persistence;

import br.com.wallet.savings.api.model.SavingsExecutionStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
public class SavingsExecutionHistoryDao {

    private final JdbcTemplate jdbc;

    public SavingsExecutionHistoryDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public void insertExecution(
            @NonNull final UUID operationId,
            @NonNull final UUID planId,
            @NonNull final UUID ruleId,
            @NonNull final UUID sourceOperationId,
            @NonNull final String triggerEventType,
            @NonNull final BigDecimal calculatedAmount,
            @NonNull final BigDecimal sweptAmount,
            @NonNull final SavingsExecutionStatus status,
            @Nullable final String errorMessage
    ) {
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Objects.requireNonNull(planId, "planId cannot be null");
        Objects.requireNonNull(ruleId, "ruleId cannot be null");
        Objects.requireNonNull(sourceOperationId, "sourceOperationId cannot be null");
        Objects.requireNonNull(triggerEventType, "triggerEventType cannot be null");
        Objects.requireNonNull(calculatedAmount, "calculatedAmount cannot be null");
        Objects.requireNonNull(sweptAmount, "sweptAmount cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        jdbc.update("""
            INSERT INTO savings_execution_history (
                operation_id, plan_id, rule_id, source_operation_id,
                trigger_event_type, calculated_amount, swept_amount,
                status, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, operationId, planId, ruleId, sourceOperationId, triggerEventType,
                calculatedAmount, sweptAmount, status.name(), errorMessage);
    }

    public boolean isOperationProcessed(@NonNull final UUID operationId) {
        Objects.requireNonNull(operationId, "operationId cannot be null");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM savings_execution_history WHERE operation_id = ?",
                Integer.class, operationId
        );
        return count != null && count > 0;
    }

    public BigDecimal getTotalSavedByWalletId(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        BigDecimal total = jdbc.queryForObject("""
            SELECT COALESCE(SUM(h.swept_amount), 0.00)
            FROM savings_execution_history h
            JOIN savings_plans p ON h.plan_id = p.id
            WHERE p.source_wallet_id = ? AND h.status = 'EXECUTED'
        """, BigDecimal.class, walletId);
        return total != null ? total : BigDecimal.ZERO;
    }

    public long getExecutionCountByWalletId(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Long count = jdbc.queryForObject("""
            SELECT COUNT(1)
            FROM savings_execution_history h
            JOIN savings_plans p ON h.plan_id = p.id
            WHERE p.source_wallet_id = ? AND h.status = 'EXECUTED'
        """, Long.class, walletId);
        return count != null ? count : 0L;
    }

    public Map<String, BigDecimal> getSavedBreakdownByRuleType(@NonNull final UUID walletId) {
        Objects.requireNonNull(walletId, "walletId cannot be null");
        return jdbc.query("""
            SELECT r.rule_type, COALESCE(SUM(h.swept_amount), 0.00) as total_amount
            FROM savings_execution_history h
            JOIN savings_plans p ON h.plan_id = p.id
            JOIN savings_rules r ON h.rule_id = r.id
            WHERE p.source_wallet_id = ? AND h.status = 'EXECUTED'
            GROUP BY r.rule_type
        """, rs -> {
            Map<String, BigDecimal> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("rule_type"), rs.getBigDecimal("total_amount"));
            }
            return map;
        }, walletId);
    }
}
