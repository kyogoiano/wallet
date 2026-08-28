package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.dlq.api.DlqManagementUseCase;
import br.com.wallet.dlq.api.DlqQueryUseCase;
import br.com.wallet.dlq.api.dto.DlqOperationResponse;
import br.com.wallet.dlq.api.dto.ReplayExhaustedResult;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.dlq.api.model.DlqStatus;
import br.com.wallet.infrastructure.rest.controller.DlqController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DlqController.class)
@DisplayName("DlqController Unit Tests")
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
    @DisplayName("Should list DLQ operations with filters (200 OK)")
    void shouldListDlqOperations() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.EXHAUSTED, "DB lock timeout", "{}", 3, null,
                Instant.now(), null, DlqFailureType.TRANSIENT, "Deposit"
        );
        when(queryUseCase.findOperations(any(), eq(50), eq(0))).thenReturn(List.of(response));

        mockMvc.perform(get("/dlq/operations?status=EXHAUSTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].status").value("EXHAUSTED"))
                .andExpect(jsonPath("$[0].retryCount").value(3));
    }

    @Test
    @DisplayName("Should get DLQ operation by ID (200 OK)")
    void shouldGetOperationById() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.EXHAUSTED, "DB lock timeout", "{}", 3, null,
                Instant.now(), null, DlqFailureType.TRANSIENT, "Deposit"
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
                Instant.now(), Instant.now(), DlqFailureType.TRANSIENT, "Deposit"
        );
        when(managementUseCase.replayOperation(eventId)).thenReturn(response);

        mockMvc.perform(post("/dlq/operations/{id}/replay", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        verify(managementUseCase).replayOperation(eventId);
    }

    @Test
    @DisplayName("Should manually discard DLQ operation (200 OK)")
    void shouldDiscardOperation() throws Exception {
        DlqOperationResponse response = new DlqOperationResponse(
                eventId, operationId, userId, "commands.deposit",
                DlqStatus.DISCARDED, "Operator discarded", "{}", 3, null,
                Instant.now(), Instant.now(), DlqFailureType.POISON, "Deposit"
        );
        when(managementUseCase.discardOperation(eq(eventId), any())).thenReturn(response);

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
