package br.com.wallet.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseCleaner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCleaner.class);

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "fraud_analyst_reviews",
            "fraud_investigation_checkpoints",
            "fraud_fusion_jobs",
            "fraud_relationship_events",
            "fraud_relationships",
            "fraud_entities",
            "dlq_operations",
            "goals",
            "cashflow_profiles",
            "savings_execution_history",
            "savings_rules",
            "savings_plans",
            "ledger",
            "accounts",
            "outbox",
            "wallet_operations"
    );

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (Exception e) {
                log.warn("Failed to delete from {}: {}", table, e.getMessage());
            }
        }
    }
}
