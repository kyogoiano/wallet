package br.com.wallet.integration.modulith;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("FraudGraphListener Integration Tests (REQ-STRM-001, REQ-STRM-005, I-STREAM-001, I-STREAM-003)")
public class FraudGraphListenerIT extends DockerProperties {

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
    @DisplayName("REQ-STRM-001: Should update relational graph projections on TransferCompletedEvent in-process without NATS")
    void shouldProjectGraphOnTransferCompletedEvent() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("300.00");

        // Seed accounts in database
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletFrom, new BigDecimal("1000.00"), userFrom
        );
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletTo, new BigDecimal("500.00"), userTo
        );

        TransferCompletedEvent event = new TransferCompletedEvent(
                walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        // Publish inside transaction boundary
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        // Await in-process listener execution and assert graph projection
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fraud_relationship_events WHERE operation_id = ?",
                    Integer.class,
                    operationId
            );
            assertThat(eventCount).isEqualTo(1);

            Integer relCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fraud_relationships WHERE source_id = ? AND target_id = ? AND relationship_type = 'TRANSFERRED_TO'",
                    Integer.class,
                    walletFrom, walletTo
            );
            assertThat(relCount).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("REQ-STRM-005 & I-STREAM-003: Duplicate event with same canonical identity must be ignored without corrupting graph")
    void shouldIgnoreDuplicateEventId() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID userFrom = UUID.randomUUID();
        UUID userTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("120.00");

        // Seed accounts
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletFrom, new BigDecimal("1000.00"), userFrom
        );
        jdbcTemplate.update(
                "INSERT INTO accounts (id, balance, version, user_id, status, last_sequence, created_at, tenant_id) VALUES (?, ?, 0, ?, 'ACTIVE', 0, NOW(), 'tenant-alpha')",
                walletTo, new BigDecimal("500.00"), userTo
        );

        TransferCompletedEvent event = new TransferCompletedEvent(
                walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        // First delivery
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fraud_relationship_events WHERE operation_id = ?",
                    Integer.class,
                    operationId
            );
            assertThat(eventCount).isEqualTo(1);
        });

        // Duplicate delivery with same operationId
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            // Count must still be 1 (no duplicate insertion or corruption)
            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fraud_relationship_events WHERE operation_id = ?",
                    Integer.class,
                    operationId
            );
            assertThat(eventCount).isEqualTo(1);
        });
    }
}
