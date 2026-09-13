package br.com.wallet.unit.core;

import br.com.wallet.edge.api.DurableOperationStatus;
import br.com.wallet.ledger.internal.persistence.WalletOperationsDao;
import br.com.wallet.ledger.internal.service.OperationStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("OperationStatusMonotonicityTest: Terminal State Monotonicity & Idempotency (REQ-PRC-021 & I-STATUS-001)")
class OperationStatusMonotonicityTest {

    @Mock
    private WalletOperationsDao walletOperationsDao;

    private OperationStateService operationStateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        operationStateService = new OperationStateService(walletOperationsDao);
    }

    @Test
    @DisplayName("REQ-PRC-021 & I-STATUS-001: COMPLETED state must be recognized as terminal")
    void shouldRecognizeCompletedAsTerminal() {
        UUID opId = UUID.randomUUID();
        DurableOperationStatus status = new DurableOperationStatus(opId, "COMPLETED", Instant.now(), "Done");
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("REQ-PRC-021 & I-STATUS-001: FAILED state must be recognized as terminal")
    void shouldRecognizeFailedAsTerminal() {
        UUID opId = UUID.randomUUID();
        DurableOperationStatus status = new DurableOperationStatus(opId, "FAILED", Instant.now(), "Error");
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("REQ-PRC-021: Non-terminal state PROCESSING must not be marked terminal")
    void shouldNotRecognizeProcessingAsTerminal() {
        UUID opId = UUID.randomUUID();
        DurableOperationStatus status = new DurableOperationStatus(opId, "PROCESSING", Instant.now(), "Working");
        assertThat(status.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("REQ-PRC-021: markOperationCompleted delegates to DAO with terminal completion semantics")
    void shouldDelegateCompletionIdempotently() {
        UUID opId = UUID.randomUUID();
        operationStateService.markOperationCompleted(opId);
        verify(walletOperationsDao, times(1)).completeOperation(opId);
    }

    @Test
    @DisplayName("REQ-PRC-021: markOperationFailed delegates to DAO")
    void shouldDelegateFailure() {
        UUID opId = UUID.randomUUID();
        operationStateService.markOperationFailed(opId, "Insufficient funds", "BUSINESS_ERROR");
        verify(walletOperationsDao, times(1)).failOperation(opId, "Insufficient funds", "BUSINESS_ERROR");
    }
}
