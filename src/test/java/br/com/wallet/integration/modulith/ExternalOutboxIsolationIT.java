package br.com.wallet.integration.modulith;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.internal.outbox.OutboxRelay;
import br.com.wallet.ledger.internal.outbox.OutboxStatus;
import br.com.wallet.ledger.internal.persistence.OutboxDao;
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
@DisplayName("External Outbox & Modulith Registry Isolation Integration Test (REQ-STRM-W02, I-STREAM-008)")
public class ExternalOutboxIsolationIT extends DockerProperties {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OutboxDao<TransferCompletedEvent> outboxDao;

    @Autowired
    private OutboxRelay outboxRelay;

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
    @DisplayName("I-STREAM-008: Outbox table egress and Modulith Event Publication Registry remain completely isolated")
    void shouldIsolateRegistryFromOutbox() {
        UUID walletFrom = UUID.randomUUID();
        UUID walletTo = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("400.00");

        TransferCompletedEvent event = new TransferCompletedEvent(
                walletFrom, walletTo, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        // Transaction publishes in-process event AND saves to outbox table
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            outboxDao.save(event);
        });

        // 1. Verify Event Publication Registry entry exists in PostgreSQL
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT id, event_type, serialized_event FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + operationId + "%"
            );
            assertThat(publications).isNotEmpty();
        });

        // 2. Verify Outbox Table entry exists independently
        List<Map<String, Object>> outboxRecords = jdbcTemplate.queryForList(
                "SELECT id, status, payload FROM outbox WHERE aggregate_id = ?",
                operationId
        );
        assertThat(outboxRecords).hasSize(1);
        assertThat(outboxRecords.getFirst().get("status")).isEqualTo(OutboxStatus.PENDING.name());

        // 3. Trigger Outbox Relay and verify outbox processing operates without touching event_publication
        outboxRelay.process();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> processedOutbox = jdbcTemplate.queryForList(
                    "SELECT status FROM outbox WHERE aggregate_id = ?",
                    operationId
            );
            assertThat(processedOutbox).isNotEmpty();
            assertThat(processedOutbox.getFirst().get("status")).isEqualTo(OutboxStatus.PROCESSED.name());
        });
    }
}
