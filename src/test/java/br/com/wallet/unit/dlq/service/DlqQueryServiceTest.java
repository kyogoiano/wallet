package br.com.wallet.unit.dlq.service;

import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.api.model.DlqEvent;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import br.com.wallet.dlq.internal.service.DlqQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqQueryService Unit Tests")
class DlqQueryServiceTest {

    @Mock
    private DlqOperationsDao dlqDao;

    private DlqQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DlqQueryService(dlqDao);
    }

    @Test
    @DisplayName("Should find DLQ operation by ID")
    void shouldFindById() {
        UUID eventId = UUID.randomUUID();
        DlqEvent event = new DlqEvent(
                eventId, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.EXHAUSTED, "err", "{}", 3, null, Instant.now(), null,
                DlqFailureType.TRANSIENT, "Deposit"
        );

        when(dlqDao.findById(eventId)).thenReturn(Optional.of(event));

        Optional<DlqOperationResponse> responseOpt = queryService.findById(eventId);
        assertThat(responseOpt).isPresent();
        assertThat(responseOpt.get().id()).isEqualTo(eventId);
        assertThat(responseOpt.get().status()).isEqualTo(DlqStatus.EXHAUSTED);
    }

    @Test
    @DisplayName("Should find DLQ operations by filter with sanitized limit/offset")
    void shouldFindByFilter() {
        DlqQueryFilter filter = new DlqQueryFilter(DlqStatus.EXHAUSTED, DlqFailureType.TRANSIENT, "Deposit", null);
        UUID eventId = UUID.randomUUID();
        DlqEvent event = new DlqEvent(
                eventId, UUID.randomUUID(), UUID.randomUUID(), "commands.deposit",
                DlqStatus.EXHAUSTED, "err", "{}", 3, null, Instant.now(), null,
                DlqFailureType.TRANSIENT, "Deposit"
        );

        when(dlqDao.findByFilter(eq(filter), eq(20), eq(0))).thenReturn(List.of(event));

        List<DlqOperationResponse> results = queryService.findOperations(filter, 20, 0);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(eventId);
    }
}
