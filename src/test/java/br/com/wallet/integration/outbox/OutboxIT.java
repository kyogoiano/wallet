package br.com.wallet.integration.outbox;

import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.infrasctructure.outbox.OutboxRelay;
import br.com.wallet.integration.outbox.publisher.FailingEventPublisher;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * This class is a suite that aims to test cases where we have:
 * success
 * fail
 * retries
 * invalid payload
 * idempotence
 * deterministic operations (operationId)
 */
public class OutboxIT extends IntegrationTestBase {

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    FailingEventPublisher failingEventPublisher;

    @Test
    void shouldProcessOutboxEvents() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);
        UUID opId = UUID.randomUUID();

        transferFundsUseCase.execute(from, to,
                new BigDecimal("50"), opId);

        outboxRelay.process();

        UUID eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(eventId).isNotNull();
        assertThat(testDataHelper.getStatus(eventId)).isEqualTo("PROCESSED");
    }

    /**
     * the relay is going to fail silently
     */
    @Test
    void shouldNotMarkEventAsProcessedOnFailure() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.execute(from, to,
                new BigDecimal("50"), opId);

        failingEventPublisher.enableFailure(true);

        assertThatCode(() -> outboxRelay.process())
                .doesNotThrowAnyException();

        UUID failedId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getStatus(failedId)).isEqualTo("FAILED");
        assertThat(testDataHelper.getRetryCount(failedId)).isEqualTo(1);
    }

    @Test
    void shouldRetryProcessingLater() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

        UUID opId = UUID.randomUUID();

        transferFundsUseCase.execute(from, to,
                new BigDecimal("50"), opId);

        // first try fails
        failingEventPublisher.enableFailure(true);
        outboxRelay.process();


        var failedId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getStatus(failedId)).isEqualTo("FAILED");

        // second try succeeds
        failingEventPublisher.enableFailure(false);
        outboxRelay.process();
        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(eventId).isNotNull();
        assertThat(testDataHelper.getStatus(eventId)).isEqualTo("PROCESSED");
        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(1);
        assertThat(testDataHelper.getProcessedAt(eventId)).isNotNull();
    }

    @Test
    void shouldHandleInvalidPayloadGracefully() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.execute(from, to,
                new BigDecimal("50"), opId);

        UUID eventId = testDataHelper.getOutboxIdByOperation(opId);

        testDataHelper.corruptOutboxPayload(eventId);

        outboxRelay.process();

        // process failed!
        assertThat(testDataHelper.getStatus(eventId)).isEqualTo("FAILED");
    }

    @Test
    void shouldNotReprocessAlreadyProcessedEvent() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.execute(from, to,
                new BigDecimal("50"), opId);

        outboxRelay.process();
        outboxRelay.process(); // second time

        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(0);
    }

    @Test
    void shouldStopRetryingAfterMaxAttempts() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.execute(from, to, new BigDecimal("50"), opId);

        failingEventPublisher.enableFailure(true);

        for (int i = 0; i < 5; i++) {
            outboxRelay.process();
        }

        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(5);
    }

    @Test
    void shouldNotProcessBeforeRetryTime() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);
        UUID opId = UUID.randomUUID();

        transferFundsUseCase.execute(from, to, new BigDecimal("50"), opId);

        failingEventPublisher.enableFailure(true);
        outboxRelay.process();

        // try again with no delay
        outboxRelay.process();

        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        // still failed as retry didn't happen on time
        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(1);
    }
}
