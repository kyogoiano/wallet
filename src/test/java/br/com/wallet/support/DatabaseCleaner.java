package br.com.wallet.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;

    public DatabaseCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void clean() {
        jdbc.execute("TRUNCATE TABLE fraud_analyst_reviews, fraud_investigation_checkpoints, fraud_fusion_jobs, fraud_relationship_events, fraud_relationships, fraud_entities, dlq_operations, goals, cashflow_profiles, savings_execution_history, savings_rules, savings_plans, ledger, accounts, outbox, wallet_operations RESTART IDENTITY CASCADE");
    }
}
