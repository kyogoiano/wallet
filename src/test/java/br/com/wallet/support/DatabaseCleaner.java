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
        jdbc.execute("TRUNCATE TABLE goals, cashflow_profiles, savings_execution_history, savings_rules, savings_plans, ledger, accounts, outbox, wallet_operations RESTART IDENTITY CASCADE");
    }
}
