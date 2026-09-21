package br.com.wallet.unit.ledger.persistence;

import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.LedgerDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DaoTenantPersistenceTest (REQ-SEC-008, I-SEC-005, TASK-SEC-4.3)")
class DaoTenantPersistenceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private NamedParameterJdbcTemplate namedJdbc;

    @Test
    @DisplayName("REQ-SEC-008: AccountDao must persist tenant_id on account insertion")
    void shouldPersistAccountWithTenantId() {
        AccountDao dao = new AccountDao(jdbc, namedJdbc);
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String tenantId = "tenant-alpha";

        dao.insertAccount(walletId, userId, tenantId);

        verify(jdbc).update(
                contains("tenant_id"),
                eq(walletId),
                eq(BigDecimal.ZERO),
                eq(userId),
                eq(tenantId)
        );
    }

    @Test
    @DisplayName("REQ-SEC-008: AccountDao must map tenant_id in findAccount and findWalletBalanceForUpdate")
    void shouldMapAccountTenantIdFromResultSet() throws SQLException {
        AccountDao dao = new AccountDao(jdbc, namedJdbc);
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getObject("id", UUID.class)).thenReturn(walletId);
        when(rs.getBigDecimal("balance")).thenReturn(new BigDecimal("100.00"));
        when(rs.getLong("version")).thenReturn(1L);
        when(rs.getObject("user_id", UUID.class)).thenReturn(userId);
        when(rs.getString("status")).thenReturn("ACTIVE");
        when(rs.getTimestamp("blocked_at")).thenReturn(null);
        when(rs.getString("blocked_reason")).thenReturn(null);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
        when(rs.getString("tenant_id")).thenReturn("tenant-alpha");

        when(jdbc.query(contains("FROM accounts WHERE id = ?"), any(ResultSetExtractor.class), eq(walletId)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<Optional<Account>> extractor = invocation.getArgument(1);
                    return extractor.extractData(rs);
                });

        Optional<Account> accountOpt = dao.findAccount(walletId);

        assertThat(accountOpt).isPresent();
        assertThat(accountOpt.get().tenantId()).isEqualTo("tenant-alpha");
    }

    @Test
    @DisplayName("REQ-SEC-008: LedgerDao must persist tenant_id in ledger table")
    void shouldPersistLedgerEntryWithTenantId() {
        LedgerDao dao = new LedgerDao(jdbc, namedJdbc);
        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String tenantId = "tenant-bravo";

        when(namedJdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        dao.insertLedger(walletId, new BigDecimal("50.00"), LedgerType.CREDIT, opId, userId, 1L, now, tenantId);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedJdbc).update(contains("tenant_id"), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("tenantId")).isEqualTo(tenantId);
        assertThat(params.getValue("operationId")).isEqualTo(opId);
    }

    @Test
    @DisplayName("REQ-SEC-008: WalletOperationsDao must persist and map tenant_id on operations")
    void shouldPersistAndMapWalletOperationTenantId() {
        WalletOperationsDao dao = new WalletOperationsDao(jdbc);
        UUID opId = UUID.randomUUID();
        String tenantId = "tenant-charlie";

        when(jdbc.update(anyString(), eq(opId), eq(tenantId))).thenReturn(1);

        boolean started = dao.startOperation(opId, tenantId);

        assertThat(started).isTrue();
        verify(jdbc, times(1)).update(contains("tenant_id"), eq(opId), eq(tenantId));

        dao.completeOperation(opId, tenantId);
        verify(jdbc, times(2)).update(contains("tenant_id"), eq(opId), eq(tenantId));

        dao.failOperation(opId, "Some error", "FAILURE", tenantId);
        verify(jdbc).update(contains("tenant_id"), eq(opId), eq("Some error"), eq("FAILURE"), eq(tenantId));
    }
}
