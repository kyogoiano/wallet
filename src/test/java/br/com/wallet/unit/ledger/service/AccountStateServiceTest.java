package br.com.wallet.unit.ledger.service;

import br.com.wallet.fraud.application.FraudService;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.ledger.internal.service.AccountStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStateService Unit Tests")
class AccountStateServiceTest {

    @Mock
    private AccountDao accountDao;
    @Mock
    private FraudService fraudService;

    @InjectMocks
    private AccountStateService accountStateService;

    private final UUID walletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Should block account in DB and sync Redis")
    void shouldBlockAccountAndSyncRedis() {
        when(accountDao.findUserId(walletId)).thenReturn(Optional.of(userId));

        accountStateService.blockAccount(walletId, "Administrative block for investigation");

        verify(accountDao).blockAccount(walletId, "Administrative block for investigation");
        verify(fraudService).blockUser(userId);
    }

    @Test
    @DisplayName("Should unblock account in DB and clear Redis")
    void shouldUnblockAccountAndClearRedis() {
        when(accountDao.findUserId(walletId)).thenReturn(Optional.of(userId));

        accountStateService.unblockAccount(walletId);

        verify(accountDao).unblockAccount(walletId);
        verify(fraudService).unblockUser(userId);
    }

    @Test
    @DisplayName("Should query account status")
    void shouldQueryAccountStatus() {
        when(accountDao.findAccountStatus(walletId)).thenReturn(Optional.of(AccountStatus.BLOCKED));

        Optional<AccountStatus> status = accountStateService.getAccountStatus(walletId);

        assertThat(status).contains(AccountStatus.BLOCKED);
    }
}
