package br.com.wallet.unit.ledger.service;

import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.internal.operation.Operation;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.OperationStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationStateServiceTest {

    @Mock
    WalletOperationsDao walletOperationsDao;

    @InjectMocks
    OperationStateService service;

    @Test
    void shouldMarkOperationCompleted() {
        UUID opId = UUID.randomUUID();

        service.markOperationCompleted(opId);

        verify(walletOperationsDao).completeOperation(opId);
    }

    @Test
    void shouldMarkOperationFailed() {
        UUID opId = UUID.randomUUID();

        service.markOperationFailed(opId, "Insufficient funds", "BUSINESS");

        verify(walletOperationsDao).failOperation(opId, "Insufficient funds", "BUSINESS");
    }

    @Test
    void shouldReturnOperationStatusWhenFound() {
        UUID opId = UUID.randomUUID();
        Instant now = Instant.now();
        Operation op = new Operation(opId, OperationStatus.FAILED, "Insufficient funds", "BUSINESS", now, now);
        when(walletOperationsDao.findOperation(opId)).thenReturn(Optional.of(op));

        var result = service.getOperationStatus(opId);

        assertThat(result).isPresent();
        assertThat(result.get().operationId()).isEqualTo(opId);
        assertThat(result.get().status()).isEqualTo(OperationStatus.FAILED);
        assertThat(result.get().errorMessage()).isEqualTo("Insufficient funds");
        assertThat(result.get().failureType()).isEqualTo("BUSINESS");
    }

    @Test
    void shouldReturnEmptyWhenOperationNotFound() {
        UUID opId = UUID.randomUUID();
        when(walletOperationsDao.findOperation(opId)).thenReturn(Optional.empty());

        var result = service.getOperationStatus(opId);

        assertThat(result).isEmpty();
    }
}
