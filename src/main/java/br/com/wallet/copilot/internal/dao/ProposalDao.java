package br.com.wallet.copilot.internal.dao;

import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProposalDao {

    private final JdbcTemplate jdbc;
    private final RowMapper<FinancialProposal> rowMapper = new ProposalRowMapper();

    public ProposalDao(@NonNull final JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
    }

    public boolean insert(@NonNull final FinancialProposal proposal) {
        Objects.requireNonNull(proposal, "proposal cannot be null");
        String sql = """
            INSERT INTO copilot_proposals (
                id, tenant_id, wallet_id, type, parameters_json, parameters_hash,
                status, idempotency_key, execution_operation_id, created_by, approved_by,
                created_at, expires_at, execution_claimed_at, execution_lease_until,
                approved_at, executed_at, execution_reference
            ) VALUES (
                ?, ?, ?, ?, ?::jsonb, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?
            ) ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
        """;

        int rows = jdbc.update(sql,
                proposal.id(),
                proposal.tenantId(),
                proposal.walletId(),
                proposal.type().name(),
                proposal.parametersJson(),
                proposal.parametersHash(),
                proposal.status().name(),
                proposal.idempotencyKey(),
                proposal.executionOperationId(),
                proposal.createdBy(),
                proposal.approvedBy(),
                Timestamp.from(proposal.createdAt()),
                Timestamp.from(proposal.expiresAt()),
                toTimestamp(proposal.executionClaimedAt()),
                toTimestamp(proposal.executionLeaseUntil()),
                toTimestamp(proposal.approvedAt()),
                toTimestamp(proposal.executedAt()),
                proposal.executionReference()
        );
        return rows > 0;
    }

    public Optional<FinancialProposal> findById(@NonNull final UUID id, @NonNull final String tenantId) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        String sql = "SELECT * FROM copilot_proposals WHERE id = ? AND tenant_id = ?";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, rowMapper, id, tenantId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<FinancialProposal> findByTenantAndIdempotencyKey(@NonNull final String tenantId,
                                                                    @NonNull final String idempotencyKey) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        String sql = "SELECT * FROM copilot_proposals WHERE tenant_id = ? AND idempotency_key = ?";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, rowMapper, tenantId, idempotencyKey));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<FinancialProposal> findByExecutionOperationId(@NonNull final String executionOperationId) {
        Objects.requireNonNull(executionOperationId, "executionOperationId cannot be null");
        String sql = "SELECT * FROM copilot_proposals WHERE execution_operation_id = ?";
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, rowMapper, executionOperationId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<FinancialProposal> findPendingByWalletId(@NonNull final String tenantId,
                                                         @NonNull final UUID walletId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        String sql = """
            SELECT * FROM copilot_proposals
            WHERE tenant_id = ? AND wallet_id = ? AND status = 'PROPOSED' AND expires_at >= NOW()
            ORDER BY created_at DESC
        """;
        return jdbc.query(sql, rowMapper, tenantId, walletId);
    }

    public boolean claimForExecution(@NonNull final UUID id,
                                     @NonNull final String tenantId,
                                     @NonNull final String approvedBy,
                                     @NonNull final Instant now,
                                     @NonNull final Instant leaseUntil) {
        String sql = """
            UPDATE copilot_proposals
            SET status = 'EXECUTING',
                approved_by = ?,
                approved_at = ?,
                execution_claimed_at = ?,
                execution_lease_until = ?
            WHERE id = ? AND tenant_id = ? AND status = 'PROPOSED' AND expires_at >= ?
        """;
        int rows = jdbc.update(sql,
                approvedBy,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(leaseUntil),
                id,
                tenantId,
                Timestamp.from(now)
        );
        return rows > 0;
    }

    public boolean renewLease(@NonNull final UUID id,
                              @NonNull final String tenantId,
                              @NonNull final Instant newLeaseUntil) {
        String sql = """
            UPDATE copilot_proposals
            SET execution_lease_until = ?
            WHERE id = ? AND tenant_id = ? AND status = 'EXECUTING'
        """;
        return jdbc.update(sql, Timestamp.from(newLeaseUntil), id, tenantId) > 0;
    }

    public boolean markExecuted(@NonNull final UUID id,
                                @NonNull final String tenantId,
                                @NonNull final Instant executedAt,
                                final String executionReference) {
        String sql = """
            UPDATE copilot_proposals
            SET status = 'EXECUTED',
                executed_at = ?,
                execution_reference = ?
            WHERE id = ? AND tenant_id = ? AND status = 'EXECUTING'
        """;
        return jdbc.update(sql, Timestamp.from(executedAt), executionReference, id, tenantId) > 0;
    }

    public boolean markRejected(@NonNull final UUID id,
                                @NonNull final String tenantId,
                                @NonNull final String rejectedBy,
                                @NonNull final Instant rejectedAt) {
        String sql = """
            UPDATE copilot_proposals
            SET status = 'REJECTED',
                approved_by = ?,
                approved_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'PROPOSED'
        """;
        return jdbc.update(sql, rejectedBy, Timestamp.from(rejectedAt), id, tenantId) > 0;
    }

    public boolean markInvalidated(@NonNull final UUID id,
                                   @NonNull final String tenantId,
                                   @NonNull final Instant invalidatedAt) {
        String sql = """
            UPDATE copilot_proposals
            SET status = 'INVALIDATED',
                executed_at = ?
            WHERE id = ? AND tenant_id = ? AND status = 'EXECUTING'
        """;
        return jdbc.update(sql, Timestamp.from(invalidatedAt), id, tenantId) > 0;
    }

    public boolean markExpired(@NonNull final UUID id,
                               @NonNull final String tenantId) {
        String sql = """
            UPDATE copilot_proposals
            SET status = 'EXPIRED'
            WHERE id = ? AND tenant_id = ? AND status = 'PROPOSED'
        """;
        return jdbc.update(sql, id, tenantId) > 0;
    }

    public List<FinancialProposal> findStaleExecutingLeases(@NonNull final Instant now) {
        String sql = """
            SELECT * FROM copilot_proposals
            WHERE status = 'EXECUTING' AND execution_lease_until < ?
            ORDER BY execution_lease_until ASC
        """;
        return jdbc.query(sql, rowMapper, Timestamp.from(now));
    }

    public int expireOverdueProposals(@NonNull final Instant now) {
        String sql = """
            UPDATE copilot_proposals
            SET status = 'EXPIRED'
            WHERE status = 'PROPOSED' AND expires_at < ?
        """;
        return jdbc.update(sql, Timestamp.from(now));
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class ProposalRowMapper implements RowMapper<FinancialProposal> {
        @Override
        public FinancialProposal mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FinancialProposal(
                    rs.getObject("id", UUID.class),
                    rs.getString("tenant_id"),
                    rs.getObject("wallet_id", UUID.class),
                    ProposalType.valueOf(rs.getString("type")),
                    rs.getString("parameters_json"),
                    Objects.requireNonNull(rs.getString("parameters_hash"), "parameters_hash cannot be null in database").trim(),
                    ProposalStatus.valueOf(rs.getString("status")),
                    rs.getString("idempotency_key"),
                    rs.getString("execution_operation_id"),
                    rs.getString("created_by"),
                    rs.getString("approved_by"),
                    toInstant(rs.getTimestamp("created_at")),
                    toInstant(rs.getTimestamp("expires_at")),
                    toInstant(rs.getTimestamp("execution_claimed_at")),
                    toInstant(rs.getTimestamp("execution_lease_until")),
                    toInstant(rs.getTimestamp("approved_at")),
                    toInstant(rs.getTimestamp("executed_at")),
                    rs.getString("execution_reference")
            );
        }

        private static Instant toInstant(Timestamp ts) {
            return ts != null ? ts.toInstant() : null;
        }
    }
}
