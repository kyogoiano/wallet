package br.com.wallet.integration.wallet;


import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.DepositFundsUseCase;
import br.com.wallet.domain.context.Deposit;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.exceptions.IdempotencyException;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.RegisterNatsProperties;
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
public class DepositFundsIT extends RegisterNatsProperties {
    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private CreateWalletUseCase createWalletUseCase;

    @Autowired
    private BalanceUseCase balanceUseCase;

    @Autowired
    private TestDataHelper testDataHelper;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    void shouldDepositFundsAndUpdateBalance() {
        // given
        UUID walletId = createWalletUseCase.execute();
        BigDecimal depositAmount = new  BigDecimal("100.00");
        UUID operationId =  UUID.randomUUID();

        // when
        depositFundsUseCase.handle(new Deposit(walletId, depositAmount, operationId));

        // then
        testDataHelper.assertBalance(walletId, depositAmount);
        assertThat(balanceUseCase.getBalance(walletId))
                .isEqualByComparingTo(depositAmount);
    }

    @Test
    void shouldBeIdempotentWhenSameOperationIdIsUsed() {
        // given
        UUID walletId = createWalletUseCase.execute(new Wallet(BigDecimal.TEN, UUID.randomUUID()));
        BigDecimal depositAmount = new BigDecimal("50.00");
        UUID operationId = UUID.randomUUID();

        // when
        depositFundsUseCase.handle(new Deposit(walletId, depositAmount, operationId));
        assertThatThrownBy(() -> depositFundsUseCase.handle(new Deposit(walletId, depositAmount, operationId))).isInstanceOf(IdempotencyException.class); // retry

        // then
        // Initial 10 + one deposit of 50 = 60
        testDataHelper.assertBalance(walletId, new BigDecimal("60.00"));

        var ledgerEntries = testDataHelper.getLedgerEntries(walletId);
        assertThat(ledgerEntries.size()).isEqualTo(2);
    }
}
