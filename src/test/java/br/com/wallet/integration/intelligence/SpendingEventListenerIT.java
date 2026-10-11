package br.com.wallet.integration.intelligence;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.intelligence.internal.listener.SpendingEventListener;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import br.com.wallet.ledger.api.event.WithdrawCompletedEvent;
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
@DisplayName("SpendingEventListener Integration Tests (REQ-INTEL-003, REQ-INTEL-004, I-INTEL-002, I-INTEL-008, I-STREAM-001)")
public class SpendingEventListenerIT extends DockerProperties {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private SpendingEventListener spendingEventListener;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("REQ-INTEL-003, I-INTEL-002: In-process consumption of TransferCompletedEvent via Spring Modulith")
    void shouldConsumeTransferCompletedEventInProcess() {
        int initialCount = spendingEventListener.getProcessedEventCount();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.00");

        var event = new TransferCompletedEvent(
                from, to, amount, opId, OperationOrigin.USER, "tenant-omega"
        );

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(spendingEventListener.getProcessedEventCount()).isGreaterThan(initialCount);
            assertThat(spendingEventListener.getLastProcessedTenantId()).isEqualTo("tenant-omega");
        });
    }

    @Test
    @DisplayName("REQ-INTEL-003, I-INTEL-002: In-process consumption of WithdrawCompletedEvent via Spring Modulith")
    void shouldConsumeWithdrawCompletedEventInProcess() {
        int initialCount = spendingEventListener.getProcessedEventCount();
        UUID walletId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("120.00");

        var event = new WithdrawCompletedEvent(
                walletId, amount, opId, "tenant-sigma"
        );

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(spendingEventListener.getProcessedEventCount()).isGreaterThan(initialCount);
            assertThat(spendingEventListener.getLastProcessedTenantId()).isEqualTo("tenant-sigma");
        });
    }

    @Test
    @DisplayName("REQ-INTEL-004, I-INTEL-008: Redelivery of identical eventId is an idempotent no-op")
    void shouldIgnoreRedeliveredEventIdIdempotently() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.90");

        var event = new TransferCompletedEvent(
                from, to, amount, opId, OperationOrigin.USER, "tenant-alpha"
        );

        // First delivery
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
        });

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(spendingEventListener.getLastProcessedTenantId()).isEqualTo("tenant-alpha");
        });

        int countAfterFirst = spendingEventListener.getProcessedEventCount();

        // Duplicate delivery with same event identity
        spendingEventListener.onTransfer(event);

        // Count should not increment
        assertThat(spendingEventListener.getProcessedEventCount()).isEqualTo(countAfterFirst);
    }
}
