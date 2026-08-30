package br.com.wallet.unit.ledger.exceptions;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.exceptions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExceptionType Unit Tests")
class ExceptionTypeTest {

    @Test
    @DisplayName("Should classify business exceptions as BUSINESS")
    void shouldClassifyBusinessExceptions() {
        assertThat(ExceptionType.parseException(new InsufficientFundsException())).isEqualTo(ExceptionType.BUSINESS);
        assertThat(ExceptionType.parseException(new AccountBlockedException(UUID.randomUUID(), "SUSPENDED"))).isEqualTo(ExceptionType.BUSINESS);
        assertThat(ExceptionType.parseException(new IdempotencyException("Already processed"))).isEqualTo(ExceptionType.BUSINESS);
        assertThat(ExceptionType.parseException(new FraudBlockedException(UUID.randomUUID(), UUID.randomUUID()))).isEqualTo(ExceptionType.BUSINESS);
        assertThat(ExceptionType.parseException(new IllegalArgumentException("Invalid param"))).isEqualTo(ExceptionType.BUSINESS);
    }

    @Test
    @DisplayName("Should classify permanent exceptions as PERMANENT")
    void shouldClassifyPermanentExceptions() {
        assertThat(ExceptionType.parseException(new PermanentException("Fatal error"))).isEqualTo(ExceptionType.PERMANENT);
        assertThat(ExceptionType.parseException(new ReplayAttackException(UUID.randomUUID()))).isEqualTo(ExceptionType.PERMANENT);
    }

    @Test
    @DisplayName("Should classify transient and unexpected runtime exceptions as TRANSIENT")
    void shouldClassifyTransientExceptions() {
        assertThat(ExceptionType.parseException(new TransientException("DB Timeout"))).isEqualTo(ExceptionType.TRANSIENT);
        assertThat(ExceptionType.parseException(new RuntimeException("Connection timeout"))).isEqualTo(ExceptionType.TRANSIENT);
        assertThat(ExceptionType.parseException(new IllegalStateException("Temporary lock unavailable"))).isEqualTo(ExceptionType.TRANSIENT);
    }

    @Test
    @DisplayName("Should classify null as UNKNOWN")
    void shouldClassifyNullAsUnknown() {
        assertThat(ExceptionType.parseException(null)).isEqualTo(ExceptionType.UNKNOWN);
    }
}
