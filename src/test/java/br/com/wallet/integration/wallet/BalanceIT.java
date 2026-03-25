package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestBase.class)
class BalanceIT {

    @Autowired
    BalanceUseCase balanceUseCase;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DELETE FROM ledger");
        jdbc.execute("DELETE FROM accounts");
        jdbc.execute("DELETE FROM outbox");
        jdbc.execute("DELETE FROM wallet_operations");
    }

    @Test
    void shouldReturnCurrentBalance() {

        UUID wallet = createWalletUseCase.execute(new BigDecimal("100"));

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance).isEqualByComparingTo("100");
    }

    @Test
    void shouldReturnUpdatedBalanceAfterTransfer() {

        UUID from = createWalletUseCase.execute(new BigDecimal("100"));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(from, to,
                new BigDecimal("40"), UUID.randomUUID());

        assertThat(balanceUseCase.getBalance(from))
                .isEqualByComparingTo("60");

        assertThat(balanceUseCase.getBalance(to))
                .isEqualByComparingTo("40");
    }

    @Test
    void shouldReturnHistoricalBalance() {

        UUID from = createWalletUseCase.execute(new BigDecimal("100"));
        UUID to = createWalletUseCase.execute();

        Instant before = Instant.now();

        transferFundsUseCase.execute(from, to,
                new BigDecimal("30"), UUID.randomUUID());

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

        UUID wallet = createWalletUseCase.execute(new BigDecimal("200"));
        UUID other = createWalletUseCase.execute();

        transferFundsUseCase.execute(wallet, other,
                new BigDecimal("50"), UUID.randomUUID());

        transferFundsUseCase.execute(wallet, other,
                new BigDecimal("30"), UUID.randomUUID());

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
}
