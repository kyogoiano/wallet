package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.WithdrawFundsUseCase;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.domain.context.Withdraw;
import br.com.wallet.exceptions.IdempotencyException;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Import(IntegrationTestBase.class)
public class WithdrawFundsIT {
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
        BigDecimal initialBalance = new BigDecimal("100.00");
        UUID walletId = createWalletUseCase.execute(new Wallet(initialBalance, UUID.randomUUID()));
        BigDecimal withdrawAmount = new BigDecimal("30.00");
        UUID operationId = UUID.randomUUID();

        // when
        withdrawFundsUseCase.execute(new Withdraw(walletId, withdrawAmount, operationId));

        // then
        testDataHelper.assertBalance(walletId, new BigDecimal("70.00"));
    }

    @Test
    void shouldFailWhenInsufficientFunds() {
        // given
        UUID walletId = createWalletUseCase.execute(new Wallet(BigDecimal.TEN, UUID.randomUUID()));
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                withdrawFundsUseCase.execute(new Withdraw(walletId, withdrawAmount, UUID.randomUUID()))
        ).isInstanceOf(br.com.wallet.exceptions.InsufficientFundsException.class);
    }

    @Test
    void shouldBeIdempotentWhenSameOperationIdIsUsed() {
        // given
        UUID walletId = createWalletUseCase.execute(new Wallet(new BigDecimal("100.00"), UUID.randomUUID()));
        BigDecimal withdrawAmount = new BigDecimal("40");
        UUID operationId = UUID.randomUUID();

        // when
        withdrawFundsUseCase.execute(new Withdraw(walletId, withdrawAmount, operationId));
        assertThatThrownBy(() -> withdrawFundsUseCase.execute(new Withdraw(walletId, withdrawAmount, operationId))).isInstanceOf(IdempotencyException.class); // retry

        // then
        // Initial 100 - one withdraw of 40 = 60
        testDataHelper.assertBalance(walletId, new BigDecimal("60.00"));
        var ledgerEntries = testDataHelper.getLedgerEntries(walletId);
        assertThat(ledgerEntries.size()).isEqualTo(2);
    }
}
