package br.com.wallet.unit.infrastructure.messaging;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.infrastructure.messaging.consumer.RetryDecision;
import br.com.wallet.infrastructure.messaging.consumer.RetryPolicy;
import br.com.wallet.ledger.api.exceptions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RetryPolicy Unit Tests")
class RetryPolicyTest {

    @Test
    @DisplayName("Should ACK IdempotencyException immediately on any attempt")
    void shouldAckIdempotencyException() {
        IdempotencyException ex = new IdempotencyException("Operation already processed: " + UUID.randomUUID());

        assertThat(RetryPolicy.decide(1, ex)).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(4, ex)).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(5, ex)).isEqualTo(RetryDecision.ACK);
    }

    @Test
    @DisplayName("Should ACK AccountBlockedException immediately")
    void shouldAckAccountBlockedException() {
        AccountBlockedException ex = new AccountBlockedException(UUID.randomUUID(), "SUSPENDED");

        assertThat(RetryPolicy.decide(1, ex)).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(5, ex)).isEqualTo(RetryDecision.ACK);
    }

    @Test
    @DisplayName("Should ACK FraudBlockedException and ReplayAttackException")
    void shouldAckFraudAndReplayExceptions() {
        FraudBlockedException fraudEx = new FraudBlockedException(UUID.randomUUID(), UUID.randomUUID());
        ReplayAttackException replayEx = new ReplayAttackException(UUID.randomUUID());

        assertThat(RetryPolicy.decide(1, fraudEx)).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(1, replayEx)).isEqualTo(RetryDecision.ACK);
    }

    @Test
    @DisplayName("Should ACK BusinessException and its subclasses")
    void shouldAckBusinessExceptions() {
        assertThat(RetryPolicy.decide(1, new InsufficientFundsException())).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(1, new AccountNotFoundException())).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(1, new UserNotAllowedException(UUID.randomUUID(), UUID.randomUUID()))).isEqualTo(RetryDecision.ACK);
        assertThat(RetryPolicy.decide(1, new CommandException("Invalid command"))).isEqualTo(RetryDecision.ACK);
    }

    @Test
    @DisplayName("Should ACK IllegalArgumentException")
    void shouldAckIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Amount must be positive");
        assertThat(RetryPolicy.decide(1, ex)).isEqualTo(RetryDecision.ACK);
    }

    @Test
    @DisplayName("Should RETRY TransientException or unexpected exceptions when deliveries < 5")
    void shouldRetryTransientExceptionsWhenUnderThreshold() {
        TransientException ex = new TransientException("DB Lock timeout");

        assertThat(RetryPolicy.decide(1, ex)).isEqualTo(RetryDecision.RETRY);
        assertThat(RetryPolicy.decide(2, ex)).isEqualTo(RetryDecision.RETRY);
        assertThat(RetryPolicy.decide(4, ex)).isEqualTo(RetryDecision.RETRY);

        RuntimeException genericEx = new RuntimeException("Connection timeout");
        assertThat(RetryPolicy.decide(1, genericEx)).isEqualTo(RetryDecision.RETRY);
    }

    @Test
    @DisplayName("Should send to DLQ when deliveries >= 5 for transient/unexpected exceptions")
    void shouldSendToDlqWhenExhausted() {
        TransientException ex = new TransientException("DB Lock timeout");
        assertThat(RetryPolicy.decide(5, ex)).isEqualTo(RetryDecision.DLQ);
        assertThat(RetryPolicy.decide(6, ex)).isEqualTo(RetryDecision.DLQ);

        RuntimeException genericEx = new RuntimeException("Connection timeout");
        assertThat(RetryPolicy.decide(5, genericEx)).isEqualTo(RetryDecision.DLQ);
    }
}
