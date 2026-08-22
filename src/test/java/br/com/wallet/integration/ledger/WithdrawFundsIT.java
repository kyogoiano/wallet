package br.com.wallet.integration.ledger;

import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.TestDataHelper;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
public class WithdrawFundsIT extends DockerProperties {
    @Autowired
    private WithdrawFundsUseCase withdrawFundsUseCase;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private TestDataHelper testDataHelper;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    void shouldWithdrawFundsAndUpdateBalance() {
        // given
        var walletId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        BigDecimal initialBalance = new BigDecimal("100.00");
        createWalletUseCase.handle(new Wallet(walletId, initialBalance, userId, UUID.randomUUID()));
        BigDecimal withdrawAmount = new BigDecimal("30.00");
        UUID operationId = UUID.randomUUID();

        // when
        withdrawFundsUseCase.handle(new Withdraw(walletId, userId, withdrawAmount, operationId));

        // then
        testDataHelper.assertBalance(walletId, new BigDecimal("70.00"));
    }

    @Test
    void shouldFailWhenInsufficientFunds() {
        // given
        var walletId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(walletId, BigDecimal.TEN, userId, UUID.randomUUID()));
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                withdrawFundsUseCase.handle(new Withdraw(walletId, userId, withdrawAmount, UUID.randomUUID()))
        ).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldBeIdempotentWhenSameOperationIdIsUsed() {
        // given
        var walletId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(walletId, new BigDecimal("100.00"), userId, UUID.randomUUID()));
        BigDecimal withdrawAmount = new BigDecimal("40");
        UUID operationId = UUID.randomUUID();

        // when
        withdrawFundsUseCase.handle(new Withdraw(walletId, userId, withdrawAmount, operationId));
        assertThatThrownBy(() -> withdrawFundsUseCase.handle(new Withdraw(walletId, userId, withdrawAmount, operationId))).isInstanceOf(IdempotencyException.class); // retry

        // then
        // Initial 100 - one withdraw of 40 = 60
        testDataHelper.assertBalance(walletId, new BigDecimal("60.00"));
        var ledgerEntries = testDataHelper.getLedgerEntries(walletId);
        assertThat(ledgerEntries.size()).isEqualTo(2);
    }
}
