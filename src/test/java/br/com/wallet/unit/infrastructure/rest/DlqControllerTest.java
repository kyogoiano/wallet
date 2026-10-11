package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.DlqQueryUseCase;
import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.exceptions.NonReplayableOperationException;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.infrastructure.rest.controller.DlqController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DlqController.class)
@DisplayName("DlqController Unit Tests (TASK-5.1, TASK-5.3, TASK-5.5)")
class DlqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DlqManagementUseCase managementUseCase;

    @MockitoBean
    private DlqQueryUseCase queryUseCase;

    private final UUID eventId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("Should list DLQ operations with filters including status, failureType, and tenantId (TASK-5.1)")
    void shouldListDlqOperationsWithFilters() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.transfer",
                DlqStatus.QUARANTINED, "AEAD tag mismatch", "{}", 0, null,
                Instant.now(), null, DlqFailureType.SECURITY, "Transfer", "tenant-alpha"
        );

        when(queryUseCase.findOperations(any(), eq(50), eq(0))).thenReturn(List.of(response));

        mockMvc.perform(get("/dlq/operations?status=QUARANTINED&failureType=SECURITY&tenantId=tenant-alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].status").value("QUARANTINED"))
                .andExpect(jsonPath("$[0].failureType").value("SECURITY"))
                .andExpect(jsonPath("$[0].tenantId").value("tenant-alpha"));

        ArgumentCaptor<DlqQueryFilter> filterCaptor = ArgumentCaptor.forClass(DlqQueryFilter.class);
        verify(queryUseCase).findOperations(filterCaptor.capture(), eq(50), eq(0));

        DlqQueryFilter captured = filterCaptor.getValue();
        assertThat(captured.status()).isEqualTo(DlqStatus.QUARANTINED);
        assertThat(captured.failureType()).isEqualTo(DlqFailureType.SECURITY);
        assertThat(captured.tenantId()).isEqualTo("tenant-alpha");
    }

    @Test
    @DisplayName("Should get DLQ operation by ID (200 OK)")
    void shouldGetOperationById() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.EXHAUSTED, "DB lock timeout", "{}", 3, null,
                Instant.now(), null, DlqFailureType.TRANSIENT, "Deposit", "tenant-alpha"
        );
        when(queryUseCase.findById(eventId)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/dlq/operations/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.operationId").value(operationId.toString()));
    }

    @Test
    @DisplayName("Should return 404 when operation not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(queryUseCase.findById(eventId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/dlq/operations/{id}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should manually replay DLQ operation (200 OK)")
    void shouldReplayOperation() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.COMPLETED, "DB lock timeout", "{}", 3, null,
                Instant.now(), Instant.now(), DlqFailureType.TRANSIENT, "Deposit", "tenant-alpha"
        );
        when(managementUseCase.replayOperation(eventId)).thenReturn(response);

        mockMvc.perform(post("/dlq/operations/{id}/replay", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(managementUseCase).replayOperation(eventId);
    }

    @Test
    @DisplayName("Should reject replay of AEAD tag mismatch with 422 Unprocessable Entity (TASK-5.3, I-TDLQ-007)")
    void shouldRejectReplayOfAeadTagMismatchWith422() throws Exception {
        when(managementUseCase.replayOperation(eventId))
                .thenThrow(new NonReplayableOperationException("Cryptographically corrupted ciphertext (AEAD tag mismatch) cannot be replayed"));

        mockMvc.perform(post("/dlq/operations/{id}/replay", eventId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NON_REPLAYABLE_OPERATION"))
                .andExpect(jsonPath("$.message").value("Cryptographically corrupted ciphertext (AEAD tag mismatch) cannot be replayed"));
    }

    @Test
    @DisplayName("Should manually discard DLQ operation with operator reason (TASK-5.5)")
    void shouldDiscardOperation() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.DISCARDED, "Operator discarded", "{}", 3, null,
                Instant.now(), Instant.now(), DlqFailureType.POISON, "Deposit", "tenant-alpha"
        );
        when(managementUseCase.discardOperation(eq(eventId), eq("Operator discarded"))).thenReturn(response);

        String requestBody = """
            {
                "reason": "Operator discarded"
            }
            """;

        mockMvc.perform(post("/dlq/operations/{id}/discard", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));
        verify(managementUseCase).discardOperation(eventId, "Operator discarded");
    }

    @Test
    @DisplayName("Should batch replay all exhausted operations (200 OK)")
    void shouldBatchReplayExhausted() throws Exception {
        ReplayExhaustedResult result = new ReplayExhaustedResult(2, List.of(eventId, UUID.randomUUID()));
        when(managementUseCase.replayAllExhausted()).thenReturn(result);

        mockMvc.perform(post("/dlq/operations/replay-exhausted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayedCount").value(2))
                .andExpect(jsonPath("$.operationIds").isArray());
    }
}
