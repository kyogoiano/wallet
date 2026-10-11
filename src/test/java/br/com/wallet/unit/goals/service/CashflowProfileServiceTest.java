package br.com.wallet.unit.goals.service;

import br.com.wallet.goals.api.dto.SaveCashflowProfileCommand;
import br.com.wallet.goals.api.model.CashflowProfile;
import br.com.wallet.goals.internal.persistence.CashflowProfileDao;
import br.com.wallet.goals.internal.service.CashflowProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Unit Tests: CashflowProfileService")
class CashflowProfileServiceTest {

    private CashflowProfileDao cashflowProfileDao;
    private CashflowProfileService service;

    @BeforeEach
    void setUp() {
        cashflowProfileDao = mock(CashflowProfileDao.class);
        service = new CashflowProfileService(cashflowProfileDao);
    }

    @Test
    @DisplayName("Should save cashflow profile successfully")
    void shouldSaveCashflowProfile() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        SaveCashflowProfileCommand command = new SaveCashflowProfileCommand(
                userId,
                walletId,
                new BigDecimal("10000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("2000.00")
        );

        when(cashflowProfileDao.findByWalletId(walletId)).thenReturn(Optional.empty());

        CashflowProfile profile = service.saveCashflowProfile(command);

        assertThat(profile.walletId()).isEqualTo(walletId);
        assertThat(profile.monthlyIncome()).isEqualByComparingTo("10000.00");
        assertThat(profile.monthlyCommittedExpenses()).isEqualByComparingTo("5000.00");
        assertThat(profile.minimumSafetyBuffer()).isEqualByComparingTo("2000.00");

        ArgumentCaptor<CashflowProfile> captor = ArgumentCaptor.forClass(CashflowProfile.class);
        verify(cashflowProfileDao).upsert(captor.capture());
        assertThat(captor.getValue().walletId()).isEqualTo(walletId);
    }

    @Test
    @DisplayName("Should retrieve cashflow profile by wallet ID")
    void shouldGetCashflowProfile() {
        UUID walletId = UUID.randomUUID();
        CashflowProfile existing = new CashflowProfile(
                UUID.randomUUID(), UUID.randomUUID(), walletId,
                new BigDecimal("8000.00"), new BigDecimal("4000.00"), new BigDecimal("1000.00"),
                Instant.now()
        );

        when(cashflowProfileDao.findByWalletId(walletId)).thenReturn(Optional.of(existing));

        Optional<CashflowProfile> result = service.getCashflowProfileByWalletId(walletId);

        assertThat(result).isPresent();
        assertThat(result.get().monthlyIncome()).isEqualByComparingTo("8000.00");
    }
}
