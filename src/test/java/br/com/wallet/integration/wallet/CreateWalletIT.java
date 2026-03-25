package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(IntegrationTestBase.class)
class CreateWalletIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    static Stream<BigDecimal> initialBalances() {
        return Stream.of(
                BigDecimal.ONE,
                new BigDecimal("10.00"),
                new BigDecimal("999999.99")
        );
    }

    @ParameterizedTest
    @MethodSource("initialBalances")
    void shouldCreateWalletWithInitialBalance(final BigDecimal initialBalance) {
        // given
        UUID walletId = UUID.randomUUID();

        // when
        jdbc.update("""
            INSERT INTO accounts (id, balance, version)
            VALUES (?, ?, 0)
        """, walletId, initialBalance);

        // then
        BigDecimal storedBalance = jdbc.queryForObject("""
            SELECT balance FROM accounts WHERE id = ?
        """, BigDecimal.class, walletId);

        assertThat(storedBalance).isEqualByComparingTo(initialBalance);
    }

    @Test
    void shouldCreateWalletWithZeroBalance() {
        // when
        var walletId = createWalletUseCase.execute();

        // then
        testDataHelper.assertWalletExists(walletId);
        testDataHelper.assertBalance(walletId, BigDecimal.ZERO);
    }



    @ParameterizedTest
    @MethodSource("initialBalances")
    void shouldCreateWalletWithGivenInitialBalance(BigDecimal initialBalance) {
        var walletId = createWalletUseCase.execute(initialBalance, UUID.randomUUID());

        testDataHelper.assertBalance(walletId, initialBalance);
    }

}
