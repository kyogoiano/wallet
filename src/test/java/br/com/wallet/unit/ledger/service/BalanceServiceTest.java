package br.com.wallet.unit.ledger.service;

import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.persistence.LedgerDao;
import br.com.wallet.ledger.internal.service.BalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BalanceService Unit Tests")
class BalanceServiceTest {

    @Mock
    private AccountDao accountDao;
    @Mock
    private LedgerDao ledgerDao;

    private BalanceService service;

    @BeforeEach
    void setUp() {
        service = new BalanceService(accountDao, ledgerDao);
    }

    @Test
    @DisplayName("Should return current balance for wallet")
    void shouldReturnBalance() {
        final UUID walletId = UUID.randomUUID();
        when(accountDao.findWalletBalance(walletId)).thenReturn(Optional.of(BigDecimal.valueOf(1500.50)));

        final BigDecimal result = service.getBalance(walletId);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(1500.50));
        verify(accountDao).findWalletBalance(walletId);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when wallet not found")
    void shouldThrowWhenWalletNotFound() {
        final UUID walletId = UUID.randomUUID();
        when(accountDao.findWalletBalance(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBalance(walletId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found");
    }

    @Test
    @DisplayName("Should return historical balance at specific timestamp")
    void shouldReturnHistoricalBalance() {
        final UUID walletId = UUID.randomUUID();
        final Instant at = Instant.parse("2026-08-20T00:00:00Z");
        when(ledgerDao.getBalanceAt(walletId, at)).thenReturn(BigDecimal.valueOf(800.00));

        final BigDecimal result = service.getHistoricalBalance(walletId, at);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(800.00));
        verify(ledgerDao).getBalanceAt(walletId, at);
    }
}
