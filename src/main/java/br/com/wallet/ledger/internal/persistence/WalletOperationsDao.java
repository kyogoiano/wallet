package br.com.wallet.ledger.internal.persistence;

import br.com.wallet.ledger.api.domain.OperationStatus;
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
        return startOperation(operationId, "default");
    }

    public boolean startOperation(@NonNull final UUID operationId, final String tenantId) {
        final var rowsAffected = jdbc.update("""
            INSERT INTO wallet_operations (operation_id, status, tenant_id)
            VALUES (?, 'PROCESSING', ?)
            ON CONFLICT (operation_id) DO NOTHING;
        """, operationId, tenantId != null ? tenantId : "default");

        return rowsAffected == 1;
    }

    public void completeOperation(@NonNull final UUID operationId) {
        completeOperation(operationId, "default");
    }

    public void completeOperation(@NonNull final UUID operationId, final String tenantId) {
        jdbc.update("""
            INSERT INTO wallet_operations (operation_id, status, created_at, updated_at, tenant_id)
            VALUES (?, 'COMPLETED', NOW(), NOW(), ?)
            ON CONFLICT (operation_id) DO UPDATE
            SET status = 'COMPLETED',
                updated_at = NOW();
        """, operationId, tenantId != null ? tenantId : "default");
    }

    public void failOperation(@NonNull final UUID operationId, final String errorMessage, final String failureType) {
        failOperation(operationId, errorMessage, failureType, "default");
    }

    public void failOperation(@NonNull final UUID operationId, final String errorMessage, final String failureType, final String tenantId) {
        jdbc.update("""
            INSERT INTO wallet_operations (operation_id, status, error_message, failure_type, created_at, updated_at, tenant_id)
            VALUES (?, 'FAILED', ?, ?, NOW(), NOW(), ?)
            ON CONFLICT (operation_id) DO UPDATE
            SET status = 'FAILED',
                error_message = EXCLUDED.error_message,
                failure_type = EXCLUDED.failure_type,
                updated_at = NOW()
            WHERE wallet_operations.status != 'COMPLETED';
        """, operationId, errorMessage, failureType, tenantId != null ? tenantId : "default");
    }

    public java.util.Optional<br.com.wallet.ledger.internal.operation.Operation> findOperation(@NonNull final UUID operationId) {
        String sql = """
            SELECT operation_id, status, error_message, failure_type, created_at, updated_at, tenant_id
            FROM wallet_operations
            WHERE operation_id = ?
        """;
        return jdbc.query(sql, (rs, rowNum) -> new br.com.wallet.ledger.internal.operation.Operation(
                UUID.fromString(rs.getString("operation_id")),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getString("error_message"),
                rs.getString("failure_type"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("tenant_id") != null ? rs.getString("tenant_id") : "default"
        ), operationId).stream().findFirst();
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
