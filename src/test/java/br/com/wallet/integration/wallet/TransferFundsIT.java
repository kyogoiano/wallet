package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.integration.wallet.scenarios.TransferScenario;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TransferFundsIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;



    static Stream<TransferScenario> transferScenarios() {
        return Stream.of(
                new TransferScenario(
                        new BigDecimal("100"),
                        new BigDecimal("50"),
                        new BigDecimal("50"),
                        new BigDecimal("50")
                ),
                new TransferScenario(
                        new BigDecimal("200"),
                        new BigDecimal("100"),
                        new BigDecimal("100"),
                        new BigDecimal("100")
                ),
                new TransferScenario(
                        new BigDecimal("50"),
                        new BigDecimal("50"),
                        new BigDecimal("0"),
                        new BigDecimal("50")
                )
        );
    }

    @ParameterizedTest
    @MethodSource("transferScenarios")
    void shouldTransferFundsAndUpdateBothBalances(
            TransferScenario scenario) {
        // given
        UUID from = testDataHelper.createWallet(scenario.initialFrom());
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

        // when
        transferFundsUseCase.execute(from, to, scenario.transferAmount(), UUID.randomUUID());

        // then
        testDataHelper.assertBalance(from, scenario.expectedFrom());
        testDataHelper.assertBalance(to, scenario.expectedTo());
    }

    @Test
    void shouldFailWhenInsufficientBalance() {
        // given
        UUID from = testDataHelper.createWallet(BigDecimal.TEN);
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

        // when / then
        assertThatThrownBy(() ->
                transferFundsUseCase.execute(from, to, new BigDecimal("50"), UUID.randomUUID())
        ).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldNotAllowTransferToSameWallet() {
        UUID wallet = testDataHelper.createWallet(new BigDecimal("100"));

        assertThatThrownBy(() ->
                transferFundsUseCase.execute(wallet, wallet, BigDecimal.TEN, UUID.randomUUID())
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldInsertOutboxEventOnTransfer() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

        UUID opId = UUID.randomUUID();

        transferFundsUseCase.execute(from, to,
                new BigDecimal("50"), opId);

        var count = testDataHelper.countProcessedOutbox(opId);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldNotDuplicateOutboxEventOnRetry() {

        UUID from = testDataHelper.createWallet(new BigDecimal("100"));
        UUID to = testDataHelper.createWallet(BigDecimal.ZERO);

        UUID opId = UUID.randomUUID();

        transferFundsUseCase.execute(from, to, new BigDecimal("50"), opId);
        transferFundsUseCase.execute(from, to, new BigDecimal("50"), opId); // retry

        var count = testDataHelper.countProcessedOutbox(opId);

        assertThat(count).isEqualTo(1);
    }

}
