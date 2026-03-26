package br.com.wallet.integration.wallet;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.application.usecase.ValidateLedgerUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Wallet;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Import(IntegrationTestBase.class)
class ValidateLedgerIT {

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

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    ValidateLedgerUseCase validateLedgerUseCase;

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
    void shouldValidateLedgerIntegrity(TransferScenario scenario) {

        UUID from = createWalletUseCase.execute(new Wallet(scenario.initialFrom(), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        // simulate transactions
        transferFundsUseCase.execute(new Transfer(from, to, scenario.transferAmount(), UUID.randomUUID()));


        var fromResult = validateLedgerUseCase.execute(from);

        assertThat(fromResult.valid()).isTrue();

        assertThat(fromResult.validatedEntriesSize()).isEqualTo(2);

        var toResult = validateLedgerUseCase.execute(to);

        assertThat(toResult.valid()).isTrue();

        assertThat(toResult.validatedEntriesSize()).isEqualTo(1);
    }

    @Test
    void shouldValidateLedgerAfterMultipleTransfers() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("300"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        var transfer50 = new Transfer(from, to, new BigDecimal("50"), UUID.randomUUID());
        var transfer100 = new Transfer(from, to, new BigDecimal("100"), UUID.randomUUID());

        transferFundsUseCase.execute(transfer50);
        transferFundsUseCase.execute(transfer100);
        //NOTE: reload uuid, so this is not a repeated transfer
        transfer50 = new Transfer(from, to, new BigDecimal("50"), UUID.randomUUID());

        transferFundsUseCase.execute(transfer50);

        var fromResult = validateLedgerUseCase.execute(from);
        var toResult = validateLedgerUseCase.execute(to);

        assertThat(fromResult.valid()).isTrue();
        assertThat(fromResult.validatedEntriesSize()).isEqualTo(4);

        assertThat(toResult.valid()).isTrue();
        assertThat(toResult.validatedEntriesSize()).isEqualTo(3);
    }

    @Test
    void shouldDetectTamperedLedger() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));

        transferFundsUseCase.execute(new Transfer(wallet, createWalletUseCase.execute(),
                new BigDecimal("50"), UUID.randomUUID()));

        // 💥 fraud
        testDataHelper.tamperFirstLedgerEntry(wallet, new BigDecimal("999"));

        var result = validateLedgerUseCase.execute(wallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Invalid hash");
    }

    @Test
    void shouldDetectBrokenSequence() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("50"), UUID.randomUUID()));

        // 💥 broke sequence
        testDataHelper.tamperSequence(wallet, 1L, 99L);

        var result = validateLedgerUseCase.execute(wallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Invalid sequence");
    }

    /**
     * Two operations were used to simulate a real chain of hashes,
     * after that broke hash chain
     * sequence	operation	type	impact
     * 1	    op1	        DEBIT	-50
     * 2	    op2	        DEBIT	-50
     * (TODO: better filter by ledge on tampering)
     */
    @Test
    void shouldDetectBrokenHashChain() {

        UUID fromWallet = createWalletUseCase.execute(new Wallet(new BigDecimal("200"), UUID.randomUUID()));
        UUID toWallet = createWalletUseCase.execute();

        var opId1 = UUID.randomUUID();
        transferFundsUseCase.execute(new Transfer(fromWallet, toWallet,
                new BigDecimal("50"), opId1));

        var opId2 = UUID.randomUUID();
        transferFundsUseCase.execute(new Transfer(fromWallet, toWallet,
                new BigDecimal("50"), opId2));

        // 💥 broke chaining (second entry) -- on the credit operation for opId1
        testDataHelper.tamperPreviousHash(fromWallet, 2L, "fake_hash", opId1);

        var result = validateLedgerUseCase.execute(fromWallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Broken chain");
    }

    /**
     * Note: As creation is at sequence 2 is for opId, as sequence 1 is for initial amount deposit
     */
    @Test
    void shouldDetectTamperedAmount() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();
        UUID opId = UUID.randomUUID();
        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("50"), opId));

        testDataHelper.tamperAmount(wallet, 2L, new BigDecimal("999"), opId);

        var result = validateLedgerUseCase.execute(wallet);

        assertThat(result.valid()).isFalse();
        assertThat(result.error()).contains("Invalid hash");
    }
}
