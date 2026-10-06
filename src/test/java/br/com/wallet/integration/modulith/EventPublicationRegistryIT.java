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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Spring Modulith Event Publication Registry Integration Tests (REQ-STRM-004, I-STREAM-001)")
public class EventPublicationRegistryIT extends DockerProperties {

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
    @DisplayName("REQ-STRM-004: Should persist event publication in PostgreSQL inside business transaction and mark complete")
    void shouldPersistAndCompleteEventsInPostgres() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("150.00");

        TransferCompletedEvent event = new TransferCompletedEvent(
                fromWallet, toWallet, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        // Execute publication inside transaction boundary
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        // Verify that event_publication table contains the publication entry
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                    "SELECT id, listener_id, event_type, serialized_event, publication_date, completion_date FROM event_publication"
            );
            assertThat(publications).isNotEmpty();

            Map<String, Object> record = publications.getFirst();
            assertThat(record.get("event_type")).asString().contains("TransferCompletedEvent");
            assertThat(record.get("serialized_event")).asString().contains(operationId.toString());
            assertThat(record.get("publication_date")).isNotNull();
            assertThat(record.get("completion_date")).isNotNull();
        });
    }

    @Test
    @DisplayName("I-STREAM-001: Should rollback event publication entry when business transaction rolls back")
    void shouldNotPersistEventWhenTransactionRollsBack() {
        UUID fromWallet = UUID.randomUUID();
        UUID toWallet = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");

        TransferCompletedEvent event = new TransferCompletedEvent(
                fromWallet, toWallet, amount, operationId, OperationOrigin.USER, "tenant-alpha"
        );

        assertThatThrownBy(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                eventPublisher.publishEvent(event);
                throw new RuntimeException("Simulated transaction rollback");
            });
        }).isInstanceOf(RuntimeException.class).hasMessage("Simulated transaction rollback");

        // Verify zero publications exist for this transaction
        List<Map<String, Object>> publications = jdbcTemplate.queryForList(
                "SELECT * FROM event_publication WHERE serialized_event LIKE ?",
                "%" + operationId + "%"
        );
        assertThat(publications).isEmpty();
    }
}
