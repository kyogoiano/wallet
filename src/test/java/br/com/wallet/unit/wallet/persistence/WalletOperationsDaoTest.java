package br.com.wallet.unit.wallet.persistence;

import br.com.wallet.wallet.internal.persistence.WalletOperationsDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletOperationsDaoTest {

    @Mock
    JdbcTemplate jdbc;

    @InjectMocks
    WalletOperationsDao dao;

    @Test
    void shouldReturnTrueWhenInsertSucceeds() {

        when(jdbc.update(anyString(), Optional.ofNullable(any())))
                .thenReturn(1);

        var result = dao.startOperation(UUID.randomUUID());

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDuplicateKeyOccurs() {

        when(jdbc.update(anyString(), Optional.ofNullable(any()))).thenReturn(0);

        var result = dao.startOperation(UUID.randomUUID());

        assertThat(result).isFalse();
    }
}
