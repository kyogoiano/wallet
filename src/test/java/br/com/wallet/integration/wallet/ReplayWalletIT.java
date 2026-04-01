package br.com.wallet.integration.wallet;


import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.ReplayWalletUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.RegisterNatsProperties;
import br.com.wallet.support.TestDataHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Import(IntegrationTestBase.class)
public class ReplayWalletIT extends RegisterNatsProperties {

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    ReplayWalletUseCase replayWalletUseCase;

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    void shouldReplayWalletBalanceCorrectly() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("200"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        var transfer50 = new Transfer(wallet, to,
                new BigDecimal("50"), UUID.randomUUID());
        transferFundsUseCase.handle(transfer50);
        var transfer30 = new Transfer(wallet, to,
                new BigDecimal("30"), UUID.randomUUID());
        transferFundsUseCase.handle(transfer30);

        var replayed = replayWalletUseCase.execute(wallet);

        assertThat(replayed).isEqualByComparingTo("120");
    }

    @Test
    void replayShouldMatchStoredBalance() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("150"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.handle(new Transfer(wallet, to,
                new BigDecimal("40"), UUID.randomUUID()));

        var replayed = replayWalletUseCase.execute(wallet);
        testDataHelper.assertBalance(wallet, replayed);
    }

    @Test
    void replayShouldStillWorkEvenIfLedgerIsCorrupted() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("200"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        var opId = UUID.randomUUID();

        transferFundsUseCase.handle(new Transfer(wallet, to,
                new BigDecimal("50"), opId));

        // 💥 tamper
        testDataHelper.tamperAmount(wallet, 2L, new BigDecimal("999"), opId);

        var replayed = replayWalletUseCase.execute(wallet);

        assertThat(replayed).isNotEqualByComparingTo("150");
    }
}
