package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.exceptions.IdempotencyException;
import br.com.wallet.exceptions.InsufficientFundsException;
import br.com.wallet.integration.wallet.scenarios.TransferScenario;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.RegisterNatsProperties;
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
class TransferFundsIT extends RegisterNatsProperties {

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
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, scenario.initialFrom(), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);

        // when
        transferFundsUseCase.handle(new Transfer(from, to, scenario.transferAmount(), UUID.randomUUID()));

        // then
        testDataHelper.assertBalance(from, scenario.expectedFrom());
        testDataHelper.assertBalance(to, scenario.expectedTo());
    }

    @Test
    void shouldFailWhenInsufficientBalance() {
        // given
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, BigDecimal.TEN, UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);

        // when / then
        assertThatThrownBy(() ->
                transferFundsUseCase.handle(new Transfer(from, to, new BigDecimal("50"), UUID.randomUUID()))
        ).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldNotAllowTransferToSameWallet() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));

        assertThatThrownBy(() ->
                transferFundsUseCase.handle(new Transfer(from, from, BigDecimal.TEN, UUID.randomUUID()))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldInsertOutboxEventOnTransfer() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);

        UUID opId = UUID.randomUUID();

        transferFundsUseCase.handle(new Transfer(from, to, new BigDecimal("50"), opId));

        var count = testDataHelper.countProcessedOutbox(opId);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldNotDuplicateOutboxEventOnRetry() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(to, BigDecimal.ONE, UUID.randomUUID()));

        UUID opId = UUID.randomUUID();
        var transfer = new Transfer(from, to, new BigDecimal("50"), opId);
        transferFundsUseCase.handle(transfer);
        assertThatThrownBy(() -> transferFundsUseCase.handle(transfer)).isInstanceOf(IdempotencyException.class); // retry

        var count = testDataHelper.countProcessedOutbox(opId);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldHaveStrictlyIncreasingSequence() {
        var from = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), UUID.randomUUID()));
        var to = UUID.randomUUID();
        createWalletUseCase.handle(to);

        var transfer50 = new Transfer(from, to, new BigDecimal("50"), UUID.randomUUID());
        transferFundsUseCase.handle(transfer50);
        var transfer10 = new Transfer(from, to, new BigDecimal("10"), UUID.randomUUID());
        transferFundsUseCase.handle(transfer10);

        var entries = testDataHelper.getLedgerEntries(from);

        assertThat(entries.get(0).sequence()).isEqualTo(1);
        assertThat(entries.get(1).sequence()).isEqualTo(2);
        assertThat(entries.get(2).sequence()).isEqualTo(3);
    }
}
