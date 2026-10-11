package br.com.wallet.unit.ledger.listener;

import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.internal.listener.CreateWalletCommandListener;
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
@DisplayName("CreateWalletCommandListener Unit Tests (REQ-STRM-009, I-STREAM-009)")
class CreateWalletCommandListenerTest {

    @Mock
    private CreateWalletUseCase useCase;

    @Mock
    private OperationStateUseCase operationStateUseCase;

    private CreateWalletCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new CreateWalletCommandListener(useCase, operationStateUseCase);
    }

    @Test
    @DisplayName("Should successfully delegate Wallet command to use case")
    void shouldInvokeUseCaseOnValidWalletCommand() {
        UUID opId = UUID.randomUUID();
        Wallet wallet = new Wallet(UUID.randomUUID(), BigDecimal.TEN, UUID.randomUUID(), opId, "tenant-alpha");

        listener.onCreateWalletCommand(wallet);

        verify(useCase).handle(wallet);
        verifyNoInteractions(operationStateUseCase);
    }

    @Test
    @DisplayName("Should mark operation failed and rethrow exception on business failure")
    void shouldMarkOperationFailedAndRethrowOnBusinessException() {
        UUID opId = UUID.randomUUID();
        Wallet wallet = new Wallet(UUID.randomUUID(), BigDecimal.TEN, UUID.randomUUID(), opId, "tenant-alpha");
        doThrow(new IllegalArgumentException("Invalid initial balance")).when(useCase).handle(wallet);

        assertThatThrownBy(() -> listener.onCreateWalletCommand(wallet))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid initial balance");

        verify(operationStateUseCase).markOperationFailed(eq(opId), eq("Invalid initial balance"), eq("BUSINESS"), eq("tenant-alpha"));
    }

    @Test
    @DisplayName("Should handle IdempotencyException silently without marking failed or rethrowing")
    void shouldHandleIdempotencyExceptionSilently() {
        UUID opId = UUID.randomUUID();
        Wallet wallet = new Wallet(UUID.randomUUID(), BigDecimal.TEN, UUID.randomUUID(), opId, "tenant-alpha");
        doThrow(new IdempotencyException("Operation already processed: " + opId)).when(useCase).handle(wallet);

        assertThatCode(() -> listener.onCreateWalletCommand(wallet))
                .doesNotThrowAnyException();

        verify(useCase).handle(wallet);
        verifyNoInteractions(operationStateUseCase);
    }
}
