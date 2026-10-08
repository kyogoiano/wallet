package br.com.wallet.integration.outbox;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.exceptions.IdempotencyException;
import br.com.wallet.infrasctructure.outbox.OutboxRelay;
import br.com.wallet.infrasctructure.outbox.OutboxStatus;
import br.com.wallet.integration.outbox.publisher.FailingEventPublisher;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.RegisterNatsProperties;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@SpringBootTest
@Import(IntegrationTestBase.class)
class OutboxIT extends RegisterNatsProperties {

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    FailingEventPublisher failingEventPublisher;
    
    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
        failingEventPublisher.failNext(0);
    }

    @Test
    void shouldProcessOutboxEvents() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);
        UUID opId = UUID.randomUUID();

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("50"), opId));

        outboxRelay.process();

        UUID eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(eventId).isNotNull();
        assertThat(testDataHelper.getStatus(eventId)).isEqualTo(OutboxStatus.PROCESSED);
    }

    /**
     * the relay is going to fail silently
     */
    @Test
    void shouldNotMarkEventAsProcessedOnFailure() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("50"), opId));

        failingEventPublisher.failNext(2); // the wallet creation with initial balance is also an event (that deposits)

        assertThatCode(() -> outboxRelay.process())
                .doesNotThrowAnyException();

        UUID failedId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getStatus(failedId)).isEqualTo(OutboxStatus.FAILED);
        assertThat(testDataHelper.getRetryCount(failedId)).isEqualTo(1);
    }

    @Test
    void shouldRetryProcessingLater() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);

        UUID opId = UUID.randomUUID();

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("50"), opId));

        // first try fails ( after wallet creation with balance)
        failingEventPublisher.failNext(2);
        outboxRelay.process();


        var failedId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getStatus(failedId)).isEqualTo(OutboxStatus.FAILED);


        testDataHelper.forceRetryNow(failedId);
        // second try succeeds
        outboxRelay.process();
        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(eventId).isNotNull();
        assertThat(testDataHelper.getStatus(eventId)).isEqualTo(OutboxStatus.PROCESSED);
        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(1);
        assertThat(testDataHelper.getProcessedAt(eventId)).isNotNull();
    }

    @Test
    void shouldHandleInvalidPayloadGracefully() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("50"), opId));

        UUID eventId = testDataHelper.getOutboxIdByOperation(opId);

        testDataHelper.corruptOutboxPayload(eventId);

        outboxRelay.process();

        // process failed!
        assertThat(testDataHelper.getStatus(eventId)).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void shouldNotReprocessAlreadyProcessedEvent() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("50"), opId));

        outboxRelay.process();
        outboxRelay.process(); // second time

        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(0);
    }

    @Test
    void shouldStopRetryingAfterMaxAttempts() {

        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.handle(new Transfer(from, to, new BigDecimal("50"), opId));

        failingEventPublisher.failNext(5);

        for (int i = 0; i < 5; i++) {
            outboxRelay.process();
            var failedId = testDataHelper.getOutboxIdByOperation(opId);
            testDataHelper.forceRetryNow(failedId);
        }

        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(4);
    }

    @Test
    void shouldNotProcessBeforeRetryTime() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);
        UUID opId = UUID.randomUUID();

        transferFundsUseCase.handle(new Transfer(from, to, new BigDecimal("50"), opId));

        failingEventPublisher.failNext(2);
        outboxRelay.process();

        // try again with no delay
        outboxRelay.process();

        var eventId = testDataHelper.getOutboxIdByOperation(opId);

        // still failed as retry didn't happen on time
        assertThat(testDataHelper.getRetryCount(eventId)).isEqualTo(1);
    }

    @Test
    void shouldNotDuplicateOutboxEventsForSameOperation() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);

        UUID opId = UUID.randomUUID();

        Transfer transfer = new Transfer(from, to, new BigDecimal("50"), opId);
        transferFundsUseCase.handle(transfer);

        assertThatThrownBy(() -> transferFundsUseCase.handle(transfer)).isInstanceOf(IdempotencyException.class);

        var events = testDataHelper.getOutboxEventsByOperation(opId);

        assertThat(events).isEqualTo(1L);
    }
}
