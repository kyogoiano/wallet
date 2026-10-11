package br.com.wallet.integration.modulith;

import br.com.wallet.fraud.domain.FraudDecision;
import br.com.wallet.ledger.api.event.FraudEvent;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("FraudEventListener Integration Tests (REQ-STRM-002, REQ-STRM-005, I-STREAM-001, I-STREAM-003)")
public class FraudEventListenerIT extends DockerProperties {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private RedisCommands<String, String> redisCommands;

    @Autowired
    private DatabaseCleaner cleaner;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("REQ-STRM-002: Should process FraudEvent in-process and enrich Redis memory without NATS")
    void shouldEnrichTimelineOnFraudEvent() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEvent event = new FraudEvent(
                userId, null, new BigDecimal("150.00"), operationId, now,
                FraudDecision.ALLOW, 15, List.of(), "tenant-alpha"
        );

        // Publish inside transaction boundary
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        // Await in-process listener execution and verify timeline entry in Redis if Redis is active
        if (redisCommands != null) {
            String timelineKey = "user:tenant-alpha:" + userId + ":tx_timeline";
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                List<String> entries = redisCommands.zrange(timelineKey, 0, -1);
                assertThat(entries).contains(operationId.toString());
            });
        }
    }

    @Test
    @DisplayName("REQ-STRM-005 & I-STREAM-003: Duplicate FraudEvent must be safely ignored without duplicate Redis entries")
    void shouldIgnoreDuplicateEventId() {
        UUID userId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEvent event = new FraudEvent(
                userId, null, new BigDecimal("200.00"), operationId, now,
                FraudDecision.ALLOW, 20, List.of(), "tenant-alpha"
        );

        // First publication
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        // Duplicate delivery
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        if (redisCommands != null) {
            String timelineKey = "user:tenant-alpha:" + userId + ":tx_timeline";
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                Long card = redisCommands.zcard(timelineKey);
                assertThat(card).isEqualTo(1L);
            });
        }
    }
}
