package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.fraud.fusion.api.CheckpointRepository;
import br.com.wallet.fraud.fusion.api.FraudSignalFusionService;
import br.com.wallet.fraud.fusion.api.FusionEvaluationDispatcher;
import br.com.wallet.fraud.fusion.api.model.CheckpointRecord;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.FraudSignalSet;
import br.com.wallet.fraud.fusion.api.model.RiskAttribution;
import br.com.wallet.fraud.fusion.api.model.RiskFusionResult;
import br.com.wallet.infrastructure.rest.controller.FraudSignalFusionController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudSignalFusionController.class)
@DisplayName("FraudSignalFusionController Unit Tests (REST Endpoints)")
class FraudSignalFusionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FusionEvaluationDispatcher dispatcher;

    @MockitoBean
    private FraudSignalFusionService fusionService;

    @MockitoBean
    private CheckpointRepository checkpointRepository;

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/fusion/dispatch/{entityId} -> should return 202 ACCEPTED when enqueued")
    void shouldDispatchEvaluationSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        when(dispatcher.dispatch(eq(entityId), eq("v1"), any(Instant.class), any()))
            .thenReturn(jobId);

        mockMvc.perform(post("/api/v1/fraud/intelligence/fusion/dispatch/{entityId}", entityId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trigger\":\"SUSPICIOUS_TX_1\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.modelVersion").value("v1"));

        verify(dispatcher).dispatch(eq(entityId), eq("v1"), any(Instant.class), eq("{\"trigger\":\"SUSPICIOUS_TX_1\"}"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/fusion/evaluate/{entityId} -> should return 200 OK with fused risk result")
    void shouldEvaluateSynchronously() throws Exception {
        UUID entityId = UUID.randomUUID();
        FraudSignalSet signals = FraudSignalSet.of(0.10, 0.70, 0.50, 0.40, 0.60);
        RiskAttribution attribution = new RiskAttribution("GRAPH_INTELLIGENCE", List.of());
        RiskFusionResult result = new RiskFusionResult(0.75, FraudDecision.REVIEW, signals, attribution);

        when(fusionService.evaluateEntity(entityId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/fraud/intelligence/fusion/evaluate/{entityId}", entityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finalRisk").value(0.75))
            .andExpect(jsonPath("$.decision").value("REVIEW"))
            .andExpect(jsonPath("$.attribution.primaryDriver").value("GRAPH_INTELLIGENCE"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/fusion/reviews/{checkpointId} -> should submit review and return 200 OK")
    void shouldSubmitReviewSuccessfully() throws Exception {
        UUID checkpointId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        CheckpointRecord checkpoint = new CheckpointRecord(
            checkpointId,
            entityId,
            "PENDING_ANALYST",
            "{}",
            0.75,
            "MEDIUM",
            Instant.now(),
            Instant.now()
        );

        when(checkpointRepository.findCheckpointById(checkpointId)).thenReturn(Optional.of(checkpoint));
        when(checkpointRepository.saveReview(eq(checkpointId), eq(entityId), eq("analyst_42"), eq("CONFIRMED_FRAUD"), any()))
            .thenReturn(reviewId);

        mockMvc.perform(post("/api/v1/fraud/intelligence/fusion/reviews/{checkpointId}", checkpointId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "analystId": "analyst_42",
                        "verdict": "CONFIRMED_FRAUD",
                        "notes": "Money mule ring"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewId").value(reviewId.toString()))
            .andExpect(jsonPath("$.checkpointId").value(checkpointId.toString()))
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.status").value("ANALYST_REVIEWED"))
            .andExpect(jsonPath("$.verdict").value("CONFIRMED_FRAUD"));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/fusion/reviews/{checkpointId} -> should return 404 when checkpoint not found")
    void shouldReturn404WhenCheckpointNotFound() throws Exception {
        UUID checkpointId = UUID.randomUUID();

        when(checkpointRepository.findCheckpointById(checkpointId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/fraud/intelligence/fusion/reviews/{checkpointId}", checkpointId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "analystId": "analyst_42",
                        "verdict": "CONFIRMED_FRAUD"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
