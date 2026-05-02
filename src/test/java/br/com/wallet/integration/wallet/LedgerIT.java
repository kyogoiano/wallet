package br.com.wallet.integration.wallet;


import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.application.usecase.LedgerUseCase;
import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.LedgerType;
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
public class LedgerIT extends RegisterNatsProperties {

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
        var walletId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(walletId, userId);

        var result = ledgerUseCase.getLedger(walletId, 100);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnLedgerEntriesAfterTransfer() {
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        transferFundsUseCase.handle(new Transfer(from, to,
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
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("200"), fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("50"), UUID.randomUUID()));

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("30"), UUID.randomUUID()));

        var result = ledgerUseCase.getLedger(from, 3);

        assertThat(result.size()).isEqualTo(3);

        assertThat(result.getFirst().sequence()).isEqualTo(1L);
        assertThat(result.get(1).sequence()).isEqualTo(2L);
        assertThat(result.get(2).sequence()).isEqualTo(3L);
    }

    @Test
    void shouldRespectLimit() {
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("200"), fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("10"), UUID.randomUUID()));

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("10"), UUID.randomUUID()));

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("10"), UUID.randomUUID()));

        var result = ledgerUseCase.getLedger(from, 2);

        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void shouldNotMixLedgerBetweenWallets() {
        var wallet1 = UUID.randomUUID();
        var userId1 = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(wallet1, new BigDecimal("100"), userId1, UUID.randomUUID()));
        var wallet2 = UUID.randomUUID();
        var userId2 = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(wallet2, new BigDecimal("100"), userId2, UUID.randomUUID()));

        var other = UUID.randomUUID();
        var userId = UUID.randomUUID();
        createWalletUseCase.handle(other, userId);

        transferFundsUseCase.handle(new Transfer(wallet1, other,
                new BigDecimal("10"), UUID.randomUUID()));

        transferFundsUseCase.handle(new Transfer(wallet2, other,
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
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        transferFundsUseCase.handle(new Transfer(from, to,
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
        var from = UUID.randomUUID();
        var fromUserId = UUID.randomUUID();
        createWalletUseCase.handle(new Wallet(from, new BigDecimal("100"), fromUserId, UUID.randomUUID()));
        var to = UUID.randomUUID();
        var toUserId = UUID.randomUUID();
        createWalletUseCase.handle(to, toUserId);

        transferFundsUseCase.handle(new Transfer(from, to,
                new BigDecimal("40"), UUID.randomUUID()));

        var ledger = ledgerUseCase.getLedger(from, 100);

        var computed = ledger.stream()
                .map(entry -> entry.type() == LedgerType.CREDIT
                        ? entry.amount()
                        : entry.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        testDataHelper.assertBalance(from, computed);

    }
}
