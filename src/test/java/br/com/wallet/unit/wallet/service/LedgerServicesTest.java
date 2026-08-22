package br.com.wallet.unit.wallet.service;

import br.com.wallet.wallet.api.domain.LedgerEntry;
import br.com.wallet.wallet.api.domain.LedgerType;
import br.com.wallet.wallet.api.domain.LedgerValidationResult;
import br.com.wallet.wallet.internal.persistence.AccountDao;
import br.com.wallet.wallet.internal.persistence.LedgerDao;
import br.com.wallet.wallet.internal.persistence.OutboxDao;
import br.com.wallet.wallet.internal.persistence.WalletOperationsDao;
import br.com.wallet.wallet.internal.service.CreateWalletService;
import br.com.wallet.wallet.internal.service.LedgerService;
import br.com.wallet.wallet.internal.service.ReplayWalletService;
import br.com.wallet.wallet.internal.service.ValidateLedgerService;
import br.com.wallet.wallet.internal.service.WalletOperationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ledger, Validation, Replay & Creation Unit Tests")
class LedgerServicesTest {

    @Mock
    private LedgerDao ledgerDao;
    @Mock
    private AccountDao accountDao;
    @Mock
    private WalletOperationsDao walletOperationsDao;
    @Mock
    private OutboxDao outboxDao;
    @Mock
    private WalletOperationService core;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneId.of("UTC"));

    @Nested
    @DisplayName("LedgerService")
    class LedgerServiceTests {
        @Test
        @DisplayName("Should fetch ledger entries with limit")
        void shouldFetchLedgerEntries() {
            final LedgerService service = new LedgerService(ledgerDao);
            final UUID walletId = UUID.randomUUID();
            final List<LedgerEntry> entries = List.of(
                    new LedgerEntry(walletId, BigDecimal.valueOf(100.00), LedgerType.CREDIT, UUID.randomUUID(),  UUID.randomUUID(), 1L, "hash", "prevHash", Instant.now())
            );
            when(ledgerDao.getLedgerEntries(walletId, 50)).thenReturn(entries);

            final List<LedgerEntry> result = service.getLedger(walletId, 50);

            assertThat(result).hasSize(1);
            verify(ledgerDao).getLedgerEntries(walletId, 50);
        }
    }

    @Nested
    @DisplayName("ValidateLedgerService")
    class ValidateLedgerServiceTests {
        @Test
        @DisplayName("Should return valid result when no corrupted entries and unbroken chain")
        void shouldValidateCleanLedger() {
            final ValidateLedgerService service = new ValidateLedgerService(ledgerDao);
            final UUID walletId = UUID.randomUUID();

            when(ledgerDao.findCorruptedEntries(walletId)).thenReturn(Collections.emptyList());
            when(ledgerDao.checkChainBroken(walletId)).thenReturn(false);

            final LedgerValidationResult result = service.execute(walletId);

            assertThat(result.valid()).isTrue();
            assertThat(result.corruptedDataSize()).isZero();
            assertThat(result.error()).isEqualTo("Ledger valid");
        }

        @Test
        @DisplayName("Should report failure when corrupted hash entries found")
        void shouldReportCorruptedEntries() {
            final ValidateLedgerService service = new ValidateLedgerService(ledgerDao);
            final UUID walletId = UUID.randomUUID();

            when(ledgerDao.findCorruptedEntries(walletId)).thenReturn(List.of(3L, 4L));

            final LedgerValidationResult result = service.execute(walletId);

            assertThat(result.valid()).isFalse();
            assertThat(result.corruptedDataSize()).isEqualTo(2);
            assertThat(result.error()).contains("Hash mismatch");
        }

        @Test
        @DisplayName("Should report failure when chain is broken")
        void shouldReportBrokenChain() {
            final ValidateLedgerService service = new ValidateLedgerService(ledgerDao);
            final UUID walletId = UUID.randomUUID();

            when(ledgerDao.findCorruptedEntries(walletId)).thenReturn(Collections.emptyList());
            when(ledgerDao.checkChainBroken(walletId)).thenReturn(true);

            final LedgerValidationResult result = service.execute(walletId);

            assertThat(result.valid()).isFalse();
            assertThat(result.error()).contains("Chain link broken");
        }
    }

    @Nested
    @DisplayName("ReplayWalletService")
    class ReplayWalletServiceTests {
        @Test
        @DisplayName("Should reconstruct balance by replaying credits and debits")
        void shouldReconstructBalanceCorrectly() {
            final ReplayWalletService service = new ReplayWalletService(ledgerDao);
            final UUID walletId = UUID.randomUUID();

            final List<LedgerEntry> entries = List.of(
                    new LedgerEntry(walletId, BigDecimal.valueOf(500.00), LedgerType.CREDIT, UUID.randomUUID(), UUID.randomUUID(), 1L, "h0", "h1", Instant.now()),
                    new LedgerEntry(walletId, BigDecimal.valueOf(150.00), LedgerType.DEBIT, UUID.randomUUID(), UUID.randomUUID(), 2L, "h1", "h2", Instant.now()),
                    new LedgerEntry(walletId, BigDecimal.valueOf(50.00), LedgerType.CREDIT, UUID.randomUUID(), UUID.randomUUID(), 3L, "h2", "h3", Instant.now())
            );
            when(ledgerDao.getLedgerEntries(walletId)).thenReturn(entries);

            final BigDecimal replayedBalance = service.execute(walletId);

            assertThat(replayedBalance).isEqualByComparingTo(BigDecimal.valueOf(400.00));
        }
    }

    @Nested
    @DisplayName("CreateWalletService")
    class CreateWalletServiceTests {
        @Test
        @DisplayName("Should create account record for sync creation")
        void shouldCreateAccountSync() {
            final CreateWalletService service = new CreateWalletService(accountDao, walletOperationsDao, outboxDao, core, clock);
            final UUID walletId = UUID.randomUUID();
            final UUID userId = UUID.randomUUID();

            service.handle(walletId, userId);

            verify(accountDao).insertAccount(walletId, userId);
        }
    }
}
