package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.application.usecase.WithdrawFundsUseCase;
import br.com.wallet.domain.context.Transfer;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(IntegrationTestBase.class)
class BalanceIT {

    @Autowired
    BalanceUseCase balanceUseCase;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    WithdrawFundsUseCase withdrawFundsUseCase;

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    void shouldReturnCurrentBalance() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance).isEqualByComparingTo("100");
    }

    @Test
    void shouldDecreaseSourceBalanceAndIncreaseTargetBalanceAfterTransfer() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(from, to,
                new BigDecimal("40"), UUID.randomUUID()));

        assertThat(balanceUseCase.getBalance(from))
                .isEqualByComparingTo("60");

        assertThat(balanceUseCase.getBalance(to))
                .isEqualByComparingTo("40");
    }

    @Test
    void shouldReturnHistoricalBalance() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        Instant before = Instant.now();

        transferFundsUseCase.execute(new Transfer(from, to,
                new BigDecimal("30"), UUID.randomUUID()));

        Instant after = before.plusMillis(10);

        // before transfer
        assertThat(balanceUseCase.getHistoricalBalance(from, before))
                .isEqualByComparingTo("100");

        // after transfer
        assertThat(balanceUseCase.getHistoricalBalance(from, after))
                .isEqualByComparingTo("70");
    }

    @Test
    void shouldCalculateHistoricalBalanceWithMultipleTransactions() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("200"), UUID.randomUUID()));
        UUID other = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(wallet, other,
                new BigDecimal("50"), UUID.randomUUID()));

        transferFundsUseCase.execute(new Transfer(wallet, other,
                new BigDecimal("30"), UUID.randomUUID()));

        Instant now = Instant.now();

        var historical = balanceUseCase.getHistoricalBalance(wallet, now);

        assertThat(historical).isEqualByComparingTo("120");
    }

    @Test
    void shouldReturnZeroWhenNoTransactions() {

        UUID wallet = createWalletUseCase.execute();

        var result = balanceUseCase.getHistoricalBalance(wallet, Instant.now());

        assertThat(result).isEqualByComparingTo("0");
    }

    /**
     * Idempotence test
     * NOTE - when we create a wallet we create a deposit operation
     */
    @Test
    void shouldNotApplySameOperationTwice() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID opId = UUID.randomUUID();

        withdrawFundsUseCase.execute(new Withdraw(wallet, new BigDecimal("30"), opId));
        assertThatThrownBy(() -> withdrawFundsUseCase.execute(new Withdraw(wallet, new BigDecimal("30"), opId))).isInstanceOf(IdempotencyException.class);

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance).isEqualByComparingTo("70");

        var ledgerEntries = testDataHelper.getLedgerEntries(wallet);
        assertThat(ledgerEntries).hasSize(2);
    }

    @Test
    void shouldHandleConcurrentWithdrawalsSafely() throws Exception {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));

        try (final var executor = Executors.newFixedThreadPool(2)) {

            var op1 = UUID.randomUUID();
            var op2 = UUID.randomUUID();

            executor.submit(() -> withdrawFundsUseCase.execute(new Withdraw(wallet, new BigDecimal("80"), op1)));
            executor.submit(() -> withdrawFundsUseCase.execute(new Withdraw(wallet, new BigDecimal("80"), op2)));

            executor.shutdown();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance.compareTo(new BigDecimal(20))).isEqualTo(0);
    }

}
