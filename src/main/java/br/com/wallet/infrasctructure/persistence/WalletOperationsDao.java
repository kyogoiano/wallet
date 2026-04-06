package br.com.wallet.infrasctructure.persistence;

import br.com.wallet.infrasctructure.operation.OperationStatus;
import org.jspecify.annotations.NonNull;
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
    public boolean startOperation(@NonNull final UUID operationId) {

        final var rowsAffected = jdbc.update("""
            INSERT INTO wallet_operations (operation_id, status)
            VALUES (?, 'PROCESSING')
            ON CONFLICT (operation_id) DO NOTHING;
        """, operationId);

        return rowsAffected == 1;
    }

    public void completeOperation(@NonNull final UUID operationId) {
        jdbc.update("""
            UPDATE wallet_operations
            SET status = 'COMPLETED'
            WHERE operation_id = ?
        """, operationId);
    }

    private boolean isDuplicateKey(DataIntegrityViolationException e) {
        return e.getMessage().contains("wallet_operations_pkey");
    }

    public Boolean operationExists(@NonNull UUID operationId) {
        String sql = "SELECT EXISTS (SELECT 1 FROM wallet_operations WHERE operation_id = ?)";
        return jdbc.queryForObject(sql, Boolean.class, operationId);
    }

    public OperationStatus getStatus(@NonNull UUID operationId) {
        String sql = "SELECT status FROM wallet_operations WHERE operation_id = ?";
        return jdbc.queryForObject(sql, OperationStatus.class, operationId);
    }


}
