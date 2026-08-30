package br.com.wallet.integration.ledger;

import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
class OperationStatusTrackingIT extends DockerProperties {

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    OperationQueryUseCase operationQueryUseCase;

    @Autowired
    OperationStateUseCase operationStateUseCase;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    void shouldTrackCompletedOperationStatusOnSuccessfulTransfer() {
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        UUID opId = UUID.randomUUID();
        transferFundsUseCase.handle(new Transfer(from, to, new BigDecimal("50"), opId));

        var status = operationQueryUseCase.getOperationStatus(opId);
        assertThat(status).isPresent();
        assertThat(status.get().operationId()).isEqualTo(opId);
        assertThat(status.get().status()).isEqualTo(OperationStatus.COMPLETED);
        assertThat(status.get().errorMessage()).isNull();
    }

    @Test
    void shouldTrackFailedOperationStatusWhenConsumerMarksFailed() {
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, BigDecimal.TEN, fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        UUID opId = UUID.randomUUID();

        // 1. Transaction fails with InsufficientFundsException
        assertThatThrownBy(() ->
                transferFundsUseCase.handle(new Transfer(from, to, new BigDecimal("50"), opId))
        ).isInstanceOf(InsufficientFundsException.class);

        // 2. Consumer marks operation failed
        operationStateUseCase.markOperationFailed(opId, "Insufficient funds", "BUSINESS");

        // 3. Querying operation returns FAILED with diagnostics
        var status = operationQueryUseCase.getOperationStatus(opId);
        assertThat(status).isPresent();
        assertThat(status.get().operationId()).isEqualTo(opId);
        assertThat(status.get().status()).isEqualTo(OperationStatus.FAILED);
        assertThat(status.get().errorMessage()).isEqualTo("Insufficient funds");
        assertThat(status.get().failureType()).isEqualTo("BUSINESS");
    }
}
