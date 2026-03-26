package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.integration.wallet.scenarios.TransferScenario;
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

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest
@Import(IntegrationTestBase.class)
class TransferFundsIT {

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
                        BigDecimal.ZERO,
                        new BigDecimal("50")
                )
        );
    }

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @ParameterizedTest
    @MethodSource("transferScenarios")
    void shouldTransferFundsAndUpdateBothBalances(
            TransferScenario scenario) {
        // given
        UUID from = createWalletUseCase.execute(new Wallet(scenario.initialFrom(), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        // when
        transferFundsUseCase.execute(new Transfer(from, to, scenario.transferAmount(), UUID.randomUUID()));

        // then
        testDataHelper.assertBalance(from, scenario.expectedFrom());
        testDataHelper.assertBalance(to, scenario.expectedTo());
    }

    @Test
    void shouldFailWhenInsufficientBalance() {
        // given
        UUID from = createWalletUseCase.execute(new Wallet(BigDecimal.TEN, UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        // when / then
        assertThatThrownBy(() ->
                transferFundsUseCase.execute(new Transfer(from, to, new BigDecimal("50"), UUID.randomUUID()))
        ).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldNotAllowTransferToSameWallet() {
        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));

        assertThatThrownBy(() ->
                transferFundsUseCase.execute(new Transfer(wallet, wallet, BigDecimal.TEN, UUID.randomUUID()))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldInsertOutboxEventOnTransfer() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        UUID opId = UUID.randomUUID();

        transferFundsUseCase.execute(new Transfer(from, to, new BigDecimal("50"), opId));

        var count = testDataHelper.countProcessedOutbox(opId);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldNotDuplicateOutboxEventOnRetry() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute(new Wallet(BigDecimal.ONE, UUID.randomUUID()));

        UUID opId = UUID.randomUUID();
        var transfer = new Transfer(from, to, new BigDecimal("50"), opId);
        transferFundsUseCase.execute(transfer);
        transferFundsUseCase.execute(transfer); // retry

        var count = testDataHelper.countProcessedOutbox(opId);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldHaveStrictlyIncreasingSequence() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        var transfer50 = new Transfer(wallet, to, new BigDecimal("50"), UUID.randomUUID());
        transferFundsUseCase.execute(transfer50);
        var transfer10 = new Transfer(wallet, to, new BigDecimal("10"), UUID.randomUUID());
        transferFundsUseCase.execute(transfer10);

        var entries = testDataHelper.getLedgerEntries(wallet);

        assertThat(entries.get(0).sequence()).isEqualTo(1);
        assertThat(entries.get(1).sequence()).isEqualTo(2);
        assertThat(entries.get(2).sequence()).isEqualTo(3);
    }
}
