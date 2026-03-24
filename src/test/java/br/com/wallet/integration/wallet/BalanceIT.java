package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.BalanceUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceIT extends IntegrationTestBase {

    @Autowired
    BalanceUseCase balanceUseCase;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    TestDataHelper testDataHelper;

    @Test
    void shouldReturnCurrentBalance() {

        UUID wallet = testDataHelper.createWallet(new BigDecimal("100"));

        var balance = balanceUseCase.getBalance(wallet);

        assertThat(balance).isEqualByComparingTo("100");
    }

    @Test
    void shouldReturnUpdatedBalanceAfterTransfer() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

        transferFundsUseCase.execute(from, to,
                new BigDecimal("40"), UUID.randomUUID());

        assertThat(balanceUseCase.getBalance(from))
                .isEqualByComparingTo("60");

        assertThat(balanceUseCase.getBalance(to))
                .isEqualByComparingTo("40");
    }

    @Test
    void shouldReturnHistoricalBalance() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

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

        UUID wallet = testDataHelper.createWallet(new BigDecimal("200"));
        UUID other = testDataHelper.createWallet(BigDecimal.ZERO);

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

        UUID wallet = testDataHelper.createWallet(BigDecimal.ZERO);

        var result = balanceUseCase.getHistoricalBalance(wallet, Instant.now());

        assertThat(result).isEqualByComparingTo("0");
    }
}
