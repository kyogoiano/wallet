package br.com.wallet.integration.wallet;


import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.LedgerUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.LedgerType;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.IntegrationTestBase;
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
public class LedgerIT {

    @Autowired
    LedgerUseCase ledgerUseCase;

    @Autowired
    TransferFundsUseCase transferFundsUseCase;

    @Autowired
    CreateWalletUseCase createWalletUseCase;

    @Autowired
    TestDataHelper testDataHelper;

    @Autowired
    DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    void shouldReturnEmptyLedgerWhenNoTransactions() {

        UUID wallet = createWalletUseCase.execute();

        var result = ledgerUseCase.getLedger(wallet, 100);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnLedgerEntriesAfterTransfer() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(from, to,
                new BigDecimal("40"), UUID.randomUUID()));

        var result = ledgerUseCase.getLedger(from, 100);

        assertThat(result.size()).isEqualTo(2);

        var entry = result.getFirst();
        assertThat(entry.amount()).isEqualByComparingTo("100");
        assertThat(entry.type()).isEqualTo(LedgerType.CREDIT);
        entry = result.get(1);
        assertThat(entry.amount()).isEqualByComparingTo("40");
        assertThat(entry.type()).isEqualTo(LedgerType.DEBIT);

    }

    @Test
    void shouldReturnEntriesOrderedBySequence() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("200"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("50"), UUID.randomUUID()));

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("30"), UUID.randomUUID()));

        var result = ledgerUseCase.getLedger(wallet, 3);

        assertThat(result.size()).isEqualTo(3);

        assertThat(result.getFirst().sequence()).isEqualTo(1L);
        assertThat(result.get(1).sequence()).isEqualTo(2L);
        assertThat(result.get(2).sequence()).isEqualTo(3L);
    }

    @Test
    void shouldRespectLimit() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("200"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("10"), UUID.randomUUID()));

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("10"), UUID.randomUUID()));

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("10"), UUID.randomUUID()));

        var result = ledgerUseCase.getLedger(wallet, 2);

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void shouldNotMixLedgerBetweenWallets() {

        UUID wallet1 = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID wallet2 = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));

        UUID other = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(wallet1, other,
                new BigDecimal("10"), UUID.randomUUID()));

        transferFundsUseCase.execute(new Transfer(wallet2, other,
                new BigDecimal("20"), UUID.randomUUID()));

        var ledger1 = ledgerUseCase.getLedger(wallet1, 100);
        var ledger2 = ledgerUseCase.getLedger(wallet2, 100);

        assertThat(ledger1.size()).isEqualTo(2);
        assertThat(ledger2.size()).isEqualTo(2);

        assertThat(ledger1.getFirst().amount()).isEqualByComparingTo("100");
        assertThat(ledger1.get(1).amount()).isEqualByComparingTo("10");
        assertThat(ledger2.getFirst().amount()).isEqualByComparingTo("100");
        assertThat(ledger2.get(1).amount()).isEqualByComparingTo("20");
    }

    @Test
    void shouldReturnCorrectTypesForDebitAndCredit() {

        UUID from = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(from, to,
                new BigDecimal("25"), UUID.randomUUID()));

        var fromLedger = ledgerUseCase.getLedger(from, 100);
        var toLedger = ledgerUseCase.getLedger(to, 100);

        assertThat(fromLedger.getFirst().type()).isEqualTo(LedgerType.CREDIT);
        assertThat(fromLedger.get(1).type()).isEqualTo(LedgerType.DEBIT);
        assertThat(toLedger.getFirst().type()).isEqualTo(LedgerType.CREDIT);
    }

    /**
     * Assert balance consistency with ledger
     */
    @Test
    void ledgerSumShouldMatchCurrentBalance() {

        UUID wallet = createWalletUseCase.execute(new Wallet(new BigDecimal("100"), UUID.randomUUID()));
        UUID to = createWalletUseCase.execute();

        transferFundsUseCase.execute(new Transfer(wallet, to,
                new BigDecimal("40"), UUID.randomUUID()));

        var ledger = ledgerUseCase.getLedger(wallet, 100);

        var computed = ledger.stream()
                .map(entry -> entry.type() == LedgerType.CREDIT
                        ? entry.amount()
                        : entry.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        testDataHelper.assertBalance(wallet, computed);

    }
}
