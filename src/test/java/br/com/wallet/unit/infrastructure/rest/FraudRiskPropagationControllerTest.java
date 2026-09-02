package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.propagation.PropagatedEntityRisk;
import br.com.wallet.fraud.intelligence.propagation.PropagationEvaluationDispatcher;
import br.com.wallet.fraud.intelligence.propagation.PropagationResult;
import br.com.wallet.fraud.intelligence.propagation.RiskPropagationEngine;
import br.com.wallet.infrastructure.rest.controller.FraudRiskPropagationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudRiskPropagationController.class)
@DisplayName("FraudRiskPropagationController Unit Tests (REST Endpoints)")
class FraudRiskPropagationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PropagationEvaluationDispatcher dispatcher;

    @MockitoBean
    private RiskPropagationEngine propagationEngine;

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/propagation/dispatch/{entityId} -> should return 202 ACCEPTED when enqueued")
    void shouldDispatchEvaluationSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        when(dispatcher.dispatch(eq(entityId), any(Instant.class), eq("v1")))
            .thenReturn(Optional.of(jobId));

        mockMvc.perform(post("/api/v1/fraud/intelligence/propagation/dispatch/{entityId}", entityId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/propagation/dispatch/{entityId} -> should return 200 OK when active job already exists")
    void shouldReturn200WhenActiveJobAlreadyExists() throws Exception {
        UUID entityId = UUID.randomUUID();

        when(dispatcher.dispatch(eq(entityId), any(Instant.class), eq("v1")))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/fraud/intelligence/propagation/dispatch/{entityId}", entityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE_EXISTS"))
            .andExpect(jsonPath("$.entityId").value(entityId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/fraud/intelligence/propagation/evaluate/{entityId} -> should return evaluated propagation results")
    void shouldReturnEvaluatedPropagationResults() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        PropagatedEntityRisk targetRisk = new PropagatedEntityRisk(
            targetId,
            0.45,
            1,
            RelationshipType.SHARED_DEVICE,
            asOf,
            "v1"
        );

        PropagationResult result = new PropagationResult(
            sourceId,
            asOf,
            Map.of(targetId, targetRisk),
            1,
            "v1"
        );

        when(propagationEngine.evaluateEntity(eq(sourceId), any(Instant.class)))
            .thenReturn(result);

        mockMvc.perform(get("/api/v1/fraud/intelligence/propagation/evaluate/{entityId}", sourceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rootSourceId").value(sourceId.toString()))
            .andExpect(jsonPath("$.pathsEvaluated").value(1))
            .andExpect(jsonPath("$.modelVersion").value("v1"))
            .andExpect(jsonPath("$.propagatedRisks." + targetId + ".propagatedRisk").value(0.45))
            .andExpect(jsonPath("$.propagatedRisks." + targetId + ".primaryRelationship").value("SHARED_DEVICE"));
    }
}
