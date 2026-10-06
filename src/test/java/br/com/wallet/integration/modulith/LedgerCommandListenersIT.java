package br.com.wallet.integration.modulith;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Ledger Bounded Context Command Listeners Integration Tests (REQ-STRM-009, I-STREAM-009)")
public class LedgerCommandListenersIT extends DockerProperties {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleaner cleaner;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("REQ-STRM-009: Should execute Transfer command in-process and complete in PostgreSQL event_publication")
    void shouldExecuteTransferCommandAndCompletePublication() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("200.00");

        // Seed accounts in database
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletFrom, new BigDecimal("1000.00"), userFrom
        );
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletTo, new BigDecimal("500.00"), userTo
        );

        Transfer transfer = new Transfer(walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(transfer);
        });

        // Assert balance updated and publication completed
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            BigDecimal fromBalance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, walletFrom);
            BigDecimal toBalance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, walletTo);

            assertThat(fromBalance).isEqualByComparingTo("800.00");
            assertThat(toBalance).isEqualByComparingTo("700.00");

            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT completion_date FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + operationId + "%"
            );
            assertThat(publications).isNotEmpty();
            assertThat(publications.getFirst().get("completion_date")).isNotNull();
        });
    }

    @Test
    @DisplayName("REQ-STRM-009: Should execute Deposit command in-process and complete in PostgreSQL event_publication")
    void shouldExecuteDepositCommandAndCompletePublication() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.00");

        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletId, new BigDecimal("500.00"), userId
        );

        Deposit deposit = new Deposit(walletId, userId, amount, operationId, "tenant-alpha");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(deposit);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            BigDecimal balance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, walletId);
            assertThat(balance).isEqualByComparingTo("650.00");

            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT completion_date FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + operationId + "%"
            );
            assertThat(publications).isNotEmpty();
            assertThat(publications.getFirst().get("completion_date")).isNotNull();
        });
    }

    @Test
    @DisplayName("REQ-STRM-009: Should execute Withdraw command in-process and complete in PostgreSQL event_publication")
    void shouldExecuteWithdrawCommandAndCompletePublication() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletId, new BigDecimal("500.00"), userId
        );

        Withdraw withdraw = new Withdraw(walletId, userId, amount, operationId, "tenant-alpha");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(withdraw);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            BigDecimal balance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, walletId);
            assertThat(balance).isEqualByComparingTo("400.00");

            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT completion_date FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + operationId + "%"
            );
            assertThat(publications).isNotEmpty();
            assertThat(publications.getFirst().get("completion_date")).isNotNull();
        });
    }

    @Test
    @DisplayName("REQ-STRM-009: Should execute CreateWallet command in-process and complete in PostgreSQL event_publication")
    void shouldExecuteCreateWalletCommandAndCompletePublication() {
        UUID walletId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("250.00");

        Wallet wallet = new Wallet(walletId, initialBalance, userId, operationId, "tenant-alpha");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(wallet);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            BigDecimal balance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, walletId);
            assertThat(balance).isEqualByComparingTo("250.00");

            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT completion_date FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + operationId + "%"
            );
            assertThat(publications).isNotEmpty();
            assertThat(publications.getFirst().get("completion_date")).isNotNull();
        });
    }

    @Test
    @DisplayName("REQ-STRM-006: Incomplete publication retained on business failure (insufficient funds)")
    void shouldRetainIncompletePublicationOnBusinessFailure() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.00");

        // Seed account with only 50.00
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletFrom, new BigDecimal("50.00"), userFrom
        );
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletTo, new BigDecimal("500.00"), userTo
        );

        Transfer transfer = new Transfer(walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha");

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(transfer);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Operation state must be FAILED in wallet_operations
            String opStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM wallet_operations WHERE operation_id = ?", String.class, operationId);
            assertThat(opStatus).isEqualTo("FAILED");

            // Event publication completion_date remains NULL
            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT completion_date FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + operationId + "%"
            );
            assertThat(publications).isNotEmpty();
            assertThat(publications.getFirst().get("completion_date")).isNull();
        });
    }
}
