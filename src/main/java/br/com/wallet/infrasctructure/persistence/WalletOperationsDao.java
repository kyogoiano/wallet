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
    public boolean tryRegister(final UUID operationId) {
        try {
            jdbc.update("""
            INSERT INTO wallet_operations (operation_id)
            VALUES (?)
        """, operationId);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                return false;
            }
            throw e;
        }
    }

    private boolean isDuplicateKey(DataIntegrityViolationException e) {
        return e.getMessage().contains("wallet_operations_pkey");
    }

    public Boolean operationExists(UUID operationId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM wallet_operations WHERE operation_id = ?)";
        return jdbc.queryForObject(sql, Boolean.class, operationId);
    }


}
