package br.com.wallet.unit.infrastructure.rest;

import br.com.wallet.fraud.embeddings.api.BehavioralEmbeddingEngine;
import br.com.wallet.fraud.embeddings.api.EmbeddingEvaluationDispatcher;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import br.com.wallet.infrastructure.rest.controller.FraudEmbeddingsController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudEmbeddingsController.class)
@DisplayName("FraudEmbeddingsController Unit Tests (REST Endpoints)")
class FraudEmbeddingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BehavioralEmbeddingEngine embeddingEngine;

    @MockitoBean
    private BehavioralFeatureStore featureStore;

    @MockitoBean
    private EmbeddingEvaluationDispatcher dispatcher;

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/embeddings/extract/{entityId} -> should return 200 OK with behavioral risk and top archetype")
    void shouldExtractAndEvaluateSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();
        ArchetypeMatch match = new ArchetypeMatch("MONEY_MULE_RAPID_DRAIN", 0.90, 0.95, 1.85, 0.855);
        BehavioralFeatureVector vector = new BehavioralFeatureVector(
            entityId, new double[16], 1.85, 45L, new BigDecimal("12000.00")
        );

        when(embeddingEngine.evaluateAndPersistRisk(entityId)).thenReturn(match);
        when(featureStore.findFeatures(entityId)).thenReturn(Optional.of(vector));

        mockMvc.perform(post("/api/v1/fraud/intelligence/embeddings/extract/{entityId}", entityId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.topArchetype").value("MONEY_MULE_RAPID_DRAIN"))
            .andExpect(jsonPath("$.behavioralRisk").value(0.855))
            .andExpect(jsonPath("$.featureMagnitude").value(1.85))
            .andExpect(jsonPath("$.transactionCount").value(45));
    }

    @Test
    @DisplayName("POST /api/v1/fraud/intelligence/embeddings/dispatch/{entityId} -> should return 202 ACCEPTED when enqueued")
    void shouldDispatchEvaluationSuccessfully() throws Exception {
        UUID entityId = UUID.randomUUID();

        when(dispatcher.dispatchEvaluation(eq(entityId), any(Instant.class))).thenReturn(true);

        mockMvc.perform(post("/api/v1/fraud/intelligence/embeddings/dispatch/{entityId}", entityId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.entityId").value(entityId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
