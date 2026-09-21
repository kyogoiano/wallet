package br.com.wallet.integration.security;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.TenantMismatchException;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.domain.LedgerType;
import br.com.wallet.ledger.internal.operation.Operation;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.LedgerDao;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.WalletOperationService;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("EdgeToCoreTenantIsolationIT: In-Transaction Tenant Boundaries (I-SEC-005, TASK-SEC-5.3)")
class EdgeToCoreTenantIsolationIT extends DockerProperties {

    @Autowired
    private TransferFundsUseCase transferFundsUseCase;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private LedgerDao ledgerDao;

    @Autowired
    private WalletOperationsDao walletOperationsDao;

    @Autowired
    private WalletOperationService walletOperationService;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    private void seedAccount(UUID walletId, UUID userId, BigDecimal initialBalance, String tenantId) {
        accountDao.insertAccount(walletId, userId, tenantId);
        if (initialBalance != null && initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            walletOperationService.applyTransaction(
                    walletId,
                    initialBalance,
                    LedgerType.CREDIT,
                    UUID.randomUUID(),
                    userId,
                    Instant.now(),
                    tenantId
            );
        }
    }

    @Test
    @DisplayName("Positive test: same-tenant accounts transfer funds successfully")
    void shouldTransferFundsSuccessfullyWithinSameTenant() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        String tenantId = "tenant-enterprise-a";

        // Seed accounts within tenant-enterprise-a
        seedAccount(fromWallet, user1, new BigDecimal("500.00"), tenantId);
        seedAccount(toWallet, user2, BigDecimal.ZERO, tenantId);

        Transfer transfer = new Transfer(fromWallet, toWallet, new BigDecimal("150.00"), opId, OperationOrigin.USER, tenantId);

        transferFundsUseCase.handle(transfer);

        BigDecimal fromBalance = accountDao.findWalletBalance(fromWallet).orElseThrow();
        BigDecimal toBalance = accountDao.findWalletBalance(toWallet).orElseThrow();

        assertThat(fromBalance.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("350.00"));
        assertThat(toBalance.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("150.00"));

        Optional<Operation> op = walletOperationsDao.findOperation(opId);
        assertThat(op).isPresent();
        assertThat(op.get().status().name()).isEqualTo("COMPLETED");
        assertThat(op.get().tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Invariant breach: cross-tenant transfer attempt is rejected with TenantMismatchException without altering balances or ledger")
    void shouldRejectCrossTenantTransferAndPreserveBalances() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        // Seed account 1 in tenant-alpha with 500.00
        seedAccount(fromWallet, user1, new BigDecimal("500.00"), "tenant-alpha");

        // Seed account 2 in tenant-beta with 100.00
        seedAccount(toWallet, user2, new BigDecimal("100.00"), "tenant-beta");

        // Attacker attempts transfer under tenant-alpha context targeting tenant-beta wallet
        Transfer crossTenantTransfer = new Transfer(
                fromWallet, toWallet, new BigDecimal("200.00"), opId, OperationOrigin.USER, "tenant-alpha"
        );

        assertThatThrownBy(() -> transferFundsUseCase.handle(crossTenantTransfer))
                .isInstanceOf(TenantMismatchException.class)
                .hasMessageContaining("Cross-tenant transfer is forbidden");

        // Mathematical Invariant assertion: balances MUST remain strictly unmodified
        BigDecimal fromBalance = accountDao.findWalletBalance(fromWallet).orElseThrow();
        BigDecimal toBalance = accountDao.findWalletBalance(toWallet).orElseThrow();
        assertThat(fromBalance.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("500.00"));
        assertThat(toBalance.setScale(2, RoundingMode.UNNECESSARY)).isEqualTo(new BigDecimal("100.00"));

        // Financial Ledger Invariant assertion: zero ledger entries created for operationId
        Integer ledgerCount = jdbc.queryForObject(
                "SELECT count(*) FROM ledger WHERE operation_id = ?",
                Integer.class,
                opId
        );
        assertThat(ledgerCount).isEqualTo(0);

        // Security Audit Invariant: operation recorded as FAILED with FORBIDDEN_TENANT_ACCESS
        Optional<Operation> recordedOp = walletOperationsDao.findOperation(opId);
        assertThat(recordedOp).isPresent();
        assertThat(recordedOp.get().status().name()).isEqualTo("FAILED");
        assertThat(recordedOp.get().failureType()).isEqualTo("FORBIDDEN_TENANT_ACCESS");
        assertThat(recordedOp.get().tenantId()).isEqualTo("tenant-alpha");
    }
}
