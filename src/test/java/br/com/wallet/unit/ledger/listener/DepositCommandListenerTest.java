package br.com.wallet.unit.ledger.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.internal.listener.DepositCommandListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositCommandListener Unit Tests (REQ-STRM-009, I-STREAM-009)")
class DepositCommandListenerTest {

    @Mock
    private DepositFundsUseCase useCase;

    @Mock
    private OperationStateUseCase operationStateUseCase;

    private DepositCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new DepositCommandListener(useCase, operationStateUseCase);
    }

    @Test
    @DisplayName("Should successfully delegate Deposit command to use case")
    void shouldInvokeUseCaseOnValidDepositCommand() {
        UUID opId = UUID.randomUUID();
        Deposit deposit = new Deposit(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER, "tenant-alpha");

        listener.onDepositCommand(deposit);

        verify(useCase).handle(deposit);
        verifyNoInteractions(operationStateUseCase);
    }

    @Test
    @DisplayName("Should mark operation failed and rethrow exception on business failure")
    void shouldMarkOperationFailedAndRethrowOnBusinessException() {
        UUID opId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Deposit deposit = new Deposit(walletId, UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER, "tenant-alpha");
        doThrow(new AccountBlockedException("Account is blocked")).when(useCase).handle(deposit);

        assertThatThrownBy(() -> listener.onDepositCommand(deposit))
                .isInstanceOf(AccountBlockedException.class)
                .hasMessage("Account is blocked");

        verify(operationStateUseCase).markOperationFailed(eq(opId), eq("Account is blocked"), eq("BUSINESS"), eq("tenant-alpha"));
    }

    @Test
    @DisplayName("Should handle IdempotencyException silently without marking failed or rethrowing")
    void shouldHandleIdempotencyExceptionSilently() {
        UUID opId = UUID.randomUUID();
        Deposit deposit = new Deposit(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER, "tenant-alpha");
        doThrow(new IdempotencyException("Operation already processed: " + opId)).when(useCase).handle(deposit);

        assertThatCode(() -> listener.onDepositCommand(deposit))
                .doesNotThrowAnyException();

        verify(useCase).handle(deposit);
        verifyNoInteractions(operationStateUseCase);
    }
}
