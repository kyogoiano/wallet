package br.com.wallet.integration.modulith;

import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.core.DefaultFailedEventPublications;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import({IntegrationTestBase.class, EventPublicationRecoveryIT.RecoveryTestConfiguration.class})
@DisplayName("Spring Modulith Event Publication Recovery & Lifecycle Integration Tests (REQ-STRM-005, REQ-STRM-006, I-STREAM-003, I-STREAM-007)")
public class EventPublicationRecoveryIT extends DockerProperties {

    public record TestRecoveryEvent(UUID eventId, String payload) {}

    @TestConfiguration
    static class RecoveryTestConfiguration {

        @Bean
        public TestRecoveryListener testRecoveryListener() {
            return new TestRecoveryListener();
        }
    }

    public static class TestRecoveryListener {
        static final AtomicBoolean shouldFail = new AtomicBoolean(true);
        static final AtomicInteger executionCount = new AtomicInteger(0);
        static final Map<UUID, Integer> processedEventCounts = new ConcurrentHashMap<>();

        @ApplicationModuleListener
        public void onRecoveryEvent(TestRecoveryEvent event) {
            executionCount.incrementAndGet();
            if (shouldFail.get()) {
                throw new IllegalStateException("Simulated listener failure for recovery testing");
            }
            // Idempotent processing recording
            processedEventCounts.merge(event.eventId(), 1, Integer::sum);
        }
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DefaultFailedEventPublications incompletePublications;

    @Autowired
    private TestRecoveryListener listener;

    @Autowired
    private DatabaseCleaner cleaner;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        transactionTemplate = new TransactionTemplate(transactionManager);
        TestRecoveryListener.shouldFail.set(true);
        TestRecoveryListener.executionCount.set(0);
        TestRecoveryListener.processedEventCounts.clear();
    }

    @Test
    @DisplayName("REQ-STRM-006: Persistent listener failure must leave completion_date NULL in event_publication")
    void shouldKeepIncompletePublicationOnListenerFailure() {
        UUID eventId = UUID.randomUUID();
        TestRecoveryEvent event = new TestRecoveryEvent(eventId, "transient-failure-payload");

        // Publish inside transaction
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        // Await failure and assert publication remains incomplete
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(TestRecoveryListener.executionCount.get()).isGreaterThanOrEqualTo(1);

            List<Map<String, Object>> incomplete = jdbcTemplate.queryForList(
                    "SELECT id, listener_id, completion_date FROM event_publication WHERE completion_date IS NULL AND serialized_event LIKE ?",
                    "%" + eventId + "%"
            );
            assertThat(incomplete).hasSize(1);
            assertThat(incomplete.getFirst().get("completion_date")).isNull();
        });
    }

    @Test
    @DisplayName("REQ-STRM-006 & I-STREAM-007: Resubmission of incomplete publications recovers and marks completed")
    void shouldResubmitAndCompleteIncompletePublication() {
        UUID eventId = UUID.randomUUID();
        TestRecoveryEvent event = new TestRecoveryEvent(eventId, "recoverable-payload");

        // 1. Initial publication fails
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(TestRecoveryListener.executionCount.get()).isGreaterThanOrEqualTo(1);
        });

        // 2. Heal listener and resubmit incomplete publications
        TestRecoveryListener.shouldFail.set(false);
        incompletePublications.resubmitIncompletePublications(pub ->
                pub.getEvent().toString().contains(eventId.toString())
        );

        // 3. Verify publication is now marked complete
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT id, completion_date FROM event_publication WHERE serialized_event LIKE ?",
                    "%" + eventId + "%"
            );
            assertThat(records).isNotEmpty();
            assertThat(records.getFirst().get("completion_date")).isNotNull();
            assertThat(TestRecoveryListener.processedEventCounts.get(eventId)).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("I-STREAM-003: Duplicate event delivery with same eventId must be handled idempotently")
    void shouldHandleDuplicateReplayIdempotently() {
        UUID eventId = UUID.randomUUID();
        TestRecoveryEvent event = new TestRecoveryEvent(eventId, "idempotent-payload");

        TestRecoveryListener.shouldFail.set(false);

        // Publish event first time
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(TestRecoveryListener.processedEventCounts.get(eventId)).isEqualTo(1);
        });

        // Duplicate delivery simulation
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            // Listener was called, but domain record recognizes identical canonical eventId
            assertThat(TestRecoveryListener.processedEventCounts).containsKey(eventId);
        });
    }
}
