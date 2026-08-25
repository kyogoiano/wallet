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
@DisplayName("Account Blocking & Lifecycle State Integration Tests (I-ACCOUNT-001)")
public class AccountBlockingIT extends DockerProperties {

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
    private DatabaseCleaner cleaner;

    private UUID activeWalletId;
    private UUID blockedWalletId;
    private UUID userId;

    @BeforeEach
    void setup() {
        cleaner.clean();
        activeWalletId = UUID.randomUUID();
        blockedWalletId = UUID.randomUUID();
        userId = UUID.randomUUID();

        createWalletUseCase.handle(activeWalletId, userId);
        createWalletUseCase.handle(blockedWalletId, UUID.randomUUID());

        // Seed initial balance in active wallet
        depositFundsUseCase.handle(new Deposit(
                activeWalletId, userId, new BigDecimal("1000.00"), UUID.randomUUID(), OperationOrigin.SYSTEM
        ));
    }

    @Test
    @DisplayName("Should reject deposit when account is blocked")
    void shouldRejectDepositOnBlockedAccount() {
        // Block the account
        accountStateUseCase.blockAccount(blockedWalletId, "Fraud suspicion");
        assertThat(accountStateUseCase.getAccountStatus(blockedWalletId)).contains(AccountStatus.BLOCKED);

        // Attempt deposit
        assertThatThrownBy(() -> depositFundsUseCase.handle(new Deposit(
                blockedWalletId, null, new BigDecimal("500.00"), UUID.randomUUID(), OperationOrigin.USER
        ))).isInstanceOf(AccountBlockedException.class);
    }

    @Test
    @DisplayName("Should reject transfer from or to blocked account")
    void shouldRejectTransferInvolvingBlockedAccount() {
        // Seed funds in blocked wallet before blocking
        depositFundsUseCase.handle(new Deposit(
                blockedWalletId, null, new BigDecimal("500.00"), UUID.randomUUID(), OperationOrigin.SYSTEM
        ));

        // Block the wallet
        accountStateUseCase.blockAccount(blockedWalletId, "Administrative block");

        // 1. Attempt transfer FROM blocked wallet
        assertThatThrownBy(() -> transferFundsUseCase.handle(new Transfer(
                blockedWalletId, activeWalletId, new BigDecimal("100.00"), UUID.randomUUID(), OperationOrigin.USER
        ))).isInstanceOf(AccountBlockedException.class);

        // 2. Attempt transfer TO blocked wallet
        assertThatThrownBy(() -> transferFundsUseCase.handle(new Transfer(
                activeWalletId, blockedWalletId, new BigDecimal("100.00"), UUID.randomUUID(), OperationOrigin.USER
        ))).isInstanceOf(AccountBlockedException.class);

        // 3. Balances remain unchanged
        assertThat(balanceUseCase.getBalance(activeWalletId)).isEqualByComparingTo("1000.00");
        assertThat(balanceUseCase.getBalance(blockedWalletId)).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Should allow operations again after account is unblocked")
    void shouldAllowOperationsAfterUnblock() {
        accountStateUseCase.blockAccount(blockedWalletId, "Temporary freeze");
        assertThat(accountStateUseCase.getAccountStatus(blockedWalletId)).contains(AccountStatus.BLOCKED);

        // Unblock
        accountStateUseCase.unblockAccount(blockedWalletId);
        assertThat(accountStateUseCase.getAccountStatus(blockedWalletId)).contains(AccountStatus.ACTIVE);

        // Subsequent deposit succeeds
        depositFundsUseCase.handle(new Deposit(
                blockedWalletId, null, new BigDecimal("250.00"), UUID.randomUUID(), OperationOrigin.USER
        ));

        assertThat(balanceUseCase.getBalance(blockedWalletId)).isEqualByComparingTo("250.00");
    }
}
