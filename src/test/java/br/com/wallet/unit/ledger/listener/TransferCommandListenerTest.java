package br.com.wallet.unit.ledger.listener;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.internal.listener.TransferCommandListener;
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
@DisplayName("TransferCommandListener Unit Tests (REQ-STRM-009, I-STREAM-009)")
class TransferCommandListenerTest {

    @Mock
    private TransferFundsUseCase useCase;

    @Mock
    private OperationStateUseCase operationStateUseCase;

    private TransferCommandListener listener;

    @BeforeEach
    void setUp() {
        listener = new TransferCommandListener(useCase, operationStateUseCase);
    }

    @Test
    @DisplayName("Should successfully delegate Transfer command to use case")
    void shouldInvokeUseCaseOnValidTransferCommand() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER, "tenant-alpha");

        listener.onTransferCommand(transfer);

        verify(useCase).handle(transfer);
        verifyNoInteractions(operationStateUseCase);
    }

    @Test
    @DisplayName("Should mark operation failed and rethrow exception on business failure")
    void shouldMarkOperationFailedAndRethrowOnBusinessException() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER, "tenant-alpha");
        doThrow(new InsufficientFundsException("Insufficient funds")).when(useCase).handle(transfer);

        assertThatThrownBy(() -> listener.onTransferCommand(transfer))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient funds");

        verify(operationStateUseCase).markOperationFailed(eq(opId), eq("Insufficient funds"), eq("BUSINESS"), eq("tenant-alpha"));
    }

    @Test
    @DisplayName("Should handle IdempotencyException silently without marking failed or rethrowing")
    void shouldHandleIdempotencyExceptionSilently() {
        UUID opId = UUID.randomUUID();
        Transfer transfer = new Transfer(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, opId, OperationOrigin.USER, "tenant-alpha");
        doThrow(new IdempotencyException("Operation already processed: " + opId)).when(useCase).handle(transfer);

        assertThatCode(() -> listener.onTransferCommand(transfer))
                .doesNotThrowAnyException();

        verify(useCase).handle(transfer);
        verifyNoInteractions(operationStateUseCase);
    }
}
