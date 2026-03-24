package br.com.wallet.infrasctructure.persistence;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class WalletOperationsDao {

    private final JdbcTemplate jdbc;

    public WalletOperationsDao(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Register Operation
     * @param operationId operation id
     * @return true if operation already exists, false otherwise
     */
    public boolean registerOperation(final UUID operationId) {
        try {
            jdbc.update("""
            INSERT INTO wallet_operations (operation_id)
            VALUES (?)
        """, operationId);
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }
}
