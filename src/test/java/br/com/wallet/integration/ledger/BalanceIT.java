package br.com.wallet.integration.ledger;

import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.CreateWalletUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Wallet;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
class BalanceIT extends DockerProperties {

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
        var wallet = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(wallet, new BigDecimal("100"), userId, UUID.randomUUID(), "tenant-alpha"));

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance).isEqualByComparingTo("100");
    }

    @Test
    void shouldDecreaseSourceBalanceAndIncreaseTargetBalanceAfterTransfer() {
        var from = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), userId, UUID.randomUUID(), "tenant-alpha"));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to, userId, "tenant-alpha");

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("40"), UUID.randomUUID(), "tenant-alpha"));

        assertThat(balanceUseCase.getBalance(from))
                .isEqualByComparingTo("60");

        assertThat(balanceUseCase.getBalance(to))
                .isEqualByComparingTo("40");
    }

    @Test
    void shouldReturnHistoricalBalance() {
        var from = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), userId, UUID.randomUUID(), "tenant-alpha"));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to, userId, "tenant-alpha");

        Instant before = Instant.now();

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("30"), UUID.randomUUID(), "tenant-alpha"));

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
        var wallet = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(wallet, new BigDecimal("200"), userId, UUID.randomUUID(), "tenant-alpha"));
        var other = UUID.randomUUID();
        createWalletUseCase.handle(other, userId, "tenant-alpha");

        transferFundsUseCase.handle(new Transfer(wallet, other,
                new BigDecimal("50"), UUID.randomUUID(), "tenant-alpha"));

        transferFundsUseCase.handle(new Transfer(wallet, other,
                new BigDecimal("30"), UUID.randomUUID(), "tenant-alpha"));

        Instant now = Instant.now();

        var historical = balanceUseCase.getHistoricalBalance(wallet, now);

        assertThat(historical).isEqualByComparingTo("120");
    }

    @Test
    void shouldReturnZeroWhenNoTransactions() {
        var wallet = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(wallet, userId, "tenant-alpha");

        var result = balanceUseCase.getHistoricalBalance(wallet, Instant.now());

        assertThat(result).isEqualByComparingTo("0");
    }

    /**
     * Idempotence test
     * NOTE - when we create a wallet we create a deposit operation
     */
    @Test
    void shouldNotApplySameOperationTwice() {
        var wallet = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(wallet, new BigDecimal("100"), userId, UUID.randomUUID(), "tenant-alpha"));
        UUID opId = UUID.randomUUID();

        withdrawFundsUseCase.handle(new Withdraw(wallet, userId, new BigDecimal("30"), opId, "tenant-alpha"));
        assertThatThrownBy(() -> withdrawFundsUseCase.handle(new Withdraw(wallet, userId, new BigDecimal("30"), opId, "tenant-alpha"))).isInstanceOf(IdempotencyException.class);

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance).isEqualByComparingTo("70");

        var ledgerEntries = testDataHelper.getLedgerEntries(wallet);
        assertThat(ledgerEntries).hasSize(2);
    }

    @Test
    void shouldHandleConcurrentWithdrawalsSafely() throws Exception {
        var wallet = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(wallet, new BigDecimal("100"), userId, UUID.randomUUID(), "tenant-alpha"));

        try (final var executor = Executors.newFixedThreadPool(2)) {

            var op1 = UUID.randomUUID();
            var op2 = UUID.randomUUID();

            executor.submit(() -> withdrawFundsUseCase.handle(new Withdraw(wallet, userId, new BigDecimal("80"), op1, "tenant-alpha")));
            executor.submit(() -> withdrawFundsUseCase.handle(new Withdraw(wallet, userId, new BigDecimal("80"), op2, "tenant-alpha")));

            executor.shutdown();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance.compareTo(new BigDecimal(20))).isEqualTo(0);
    }

}
