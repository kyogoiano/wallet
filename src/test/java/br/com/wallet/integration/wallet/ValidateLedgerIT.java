package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.application.usecase.ValidateLedgerUseCase;
import br.com.wallet.integration.wallet.scenarios.TransferScenario;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ValidateLedgerIT {

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    ValidateLedgerUseCase validateLedgerUseCase;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

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
    void shouldValidateLedgerIntegrity(TransferScenario scenario) {

        UUID from = createWalletUseCase.execute(scenario.initialFrom());
        UUID to = createWalletUseCase.execute();

        // simulate transactions
        transferFundsUseCase.execute(from, to, scenario.transferAmount(), UUID.randomUUID());


        var fromResult = validateLedgerUseCase.execute(from);

        assertThat(fromResult.valid()).isTrue();

        assertThat(fromResult.validatedEntriesSize()).isEqualTo(1);

        var toResult = validateLedgerUseCase.execute(to);

        assertThat(toResult.valid()).isTrue();

        assertThat(toResult.validatedEntriesSize()).isEqualTo(1);
    }

    @Test
    void shouldValidateLedgerAfterMultipleTransfers() {

        UUID from = createWalletUseCase.execute(new BigDecimal("300"));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(from, to, new BigDecimal("50"), UUID.randomUUID());
        transferFundsUseCase.execute(from, to, new BigDecimal("100"), UUID.randomUUID());
        transferFundsUseCase.execute(from, to, new BigDecimal("50"), UUID.randomUUID());

        var fromResult = validateLedgerUseCase.execute(from);
        var toResult = validateLedgerUseCase.execute(to);

        assertThat(fromResult.valid()).isTrue();
        assertThat(fromResult.validatedEntriesSize()).isEqualTo(3);

        assertThat(toResult.valid()).isTrue();
        assertThat(toResult.validatedEntriesSize()).isEqualTo(3);
    }

    @Test
    void shouldDetectTamperedLedger() {

        UUID wallet = createWalletUseCase.execute(new BigDecimal("100"));

        transferFundsUseCase.execute(wallet, createWalletUseCase.execute(BigDecimal.ZERO),
                new BigDecimal("50"), UUID.randomUUID());

        // 💥 fraud
        testDataHelper.tamperFirstLedgerEntry(wallet, new BigDecimal("999"));

        var result = validateLedgerUseCase.execute(wallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Invalid hash");
    }

    @Test
    void shouldDetectBrokenSequence() {

        UUID wallet = createWalletUseCase.execute(new BigDecimal("100"));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(wallet, to,
                new BigDecimal("50"), UUID.randomUUID());

        // 💥 broke sequence
        testDataHelper.tamperSequence(wallet, 1L, 99L);

        var result = validateLedgerUseCase.execute(wallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Invalid sequence");
    }

    /**
     * Two operations were used to simulate a real chain of hashes,
     * after that broke hash chain
     */
    @Test
    void shouldDetectBrokenHashChain() {

        UUID wallet = createWalletUseCase.execute(new BigDecimal("200"));
        UUID to = createWalletUseCase.execute();


        transferFundsUseCase.execute(wallet, to,
                new BigDecimal("50"), UUID.randomUUID());

        transferFundsUseCase.execute(wallet, to,
                new BigDecimal("50"), UUID.randomUUID());

        // 💥 broke chaining (second entry)
        testDataHelper.tamperPreviousHash(wallet, 2L, "fake_hash");

        var result = validateLedgerUseCase.execute(wallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Broken chain");
    }
}
