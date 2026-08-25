package br.com.wallet.integration.ledger;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.ledger.api.AccountStateUseCase;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("Fraud Detection to Persistent DB Blocking Integration Tests (REQ-ACC-004 & I-ACCOUNT-001)")
public class FraudReactionIT extends DockerProperties {

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private TransferFundsUseCase transferFundsUseCase;

    @Autowired
    private BalanceUseCase balanceUseCase;

    @Autowired
    private AccountStateUseCase accountStateUseCase;

    @Autowired
    private FraudCheckHelper fraudCheckHelper;

    @Autowired
    private DatabaseCleaner cleaner;

    private UUID userWalletId;
    private UUID merchantWalletId;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        userWalletId = UUID.randomUUID();
        merchantWalletId = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(userWalletId, userId);
        createWalletUseCase.handle(merchantWalletId, UUID.randomUUID());

        // Seed initial balance in user wallet
        depositFundsUseCase.handle(new Deposit(
                userWalletId, userId, new BigDecimal("1000.00"), UUID.randomUUID(), OperationOrigin.SYSTEM
        ));
    }

    @Test
    @DisplayName("Should persistently block account in DB when fraud is triggered, and reject subsequent attempts")
    void shouldPersistentlyBlockAccountOnFraud() {
        // Given an account with active status
        assertThat(accountStateUseCase.getAccountStatus(userWalletId)).contains(AccountStatus.ACTIVE);

        // When: Fraud block is triggered via administrative or fraud rule
        accountStateUseCase.blockAccount(userWalletId, "Fraud risk score: 100");

        // Then: Account status in PostgreSQL is BLOCKED
        assertThat(accountStateUseCase.getAccountStatus(userWalletId)).contains(AccountStatus.BLOCKED);

        // And: Subsequent transfer is rejected with AccountBlockedException
        assertThatThrownBy(() -> transferFundsUseCase.handle(new Transfer(
                userWalletId, merchantWalletId, new BigDecimal("50.00"), UUID.randomUUID(), OperationOrigin.USER
        ))).isInstanceOf(AccountBlockedException.class);

        // And: Subsequent deposit is rejected with AccountBlockedException
        assertThatThrownBy(() -> depositFundsUseCase.handle(new Deposit(
                userWalletId, userId, new BigDecimal("100.00"), UUID.randomUUID(), OperationOrigin.USER
        ))).isInstanceOf(AccountBlockedException.class);
    }
}
