package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.ClaimType;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.internal.inference.OllamaInferenceClient;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.fraud.investigation.spi.InferenceModelProfile;
import br.com.wallet.fraud.investigation.spi.StructuredInferenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("OllamaInferenceClient Unit Tests (RestClient Communication to /api/chat)")
class OllamaInferenceClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private OllamaInferenceClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();
        client = new OllamaInferenceClient(restClientBuilder, "http://localhost:11434", objectMapper);
    }

    @Test
    @DisplayName("REQ-VEC-005: Should communicate with Ollama /api/chat and parse structured narrative")
    void shouldCallOllamaAndParseNarrative() {
        String ollamaResponse = """
            {
              "model": "llama3.2:3b",
              "message": {
                "role": "assistant",
                "content": "{\\"executiveSummary\\": \\"Account shows high mule indicators.\\", \\"claims\\": [{\\"type\\": \\"SHARED_INFRASTRUCTURE\\", \\"summary\\": \\"Shared device cluster.\\", \\"evidenceReferences\\": [\\"GRAPH-001\\"]}], \\"actionRationale\\": \\"Restricting transfers.\\"}"
              },
              "done": true
            }
            """;

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(ollamaResponse, MediaType.APPLICATION_JSON));

        SanitizedInferenceContext context = new SanitizedInferenceContext(
            "MASK_USER_TARGET", FraudRiskSnapshot.empty(), List.of(), RiskClassification.HIGH, List.of(RecommendedAction.MANUAL_REVIEW)
        );

        StructuredInferenceRequest request = new StructuredInferenceRequest(
            context, InferenceCapability.BALANCED, InferenceModelProfile.balanced(), "System prompt", "{}"
        );

        Optional<InvestigationNarrative> result = client.generateNarrative(request);

        assertThat(result).isPresent();
        InvestigationNarrative narrative = result.get();
        assertThat(narrative.executiveSummary()).isEqualTo("Account shows high mule indicators.");
        assertThat(narrative.actionRationale()).isEqualTo("Restricting transfers.");
        assertThat(narrative.claims()).hasSize(1);
        assertThat(narrative.claims().getFirst().type()).isEqualTo(ClaimType.SHARED_INFRASTRUCTURE);
        assertThat(narrative.claims().getFirst().evidenceReferences()).containsExactly("GRAPH-001");

        mockServer.verify();
    }

    @Test
    @DisplayName("I-VEC-009: Should return empty Optional when Ollama endpoint returns server error")
    void shouldReturnEmptyWhenServerError() {
        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        SanitizedInferenceContext context = new SanitizedInferenceContext(
            "MASK_USER_TARGET", FraudRiskSnapshot.empty(), List.of(), RiskClassification.HIGH, List.of(RecommendedAction.MANUAL_REVIEW)
        );

        StructuredInferenceRequest request = new StructuredInferenceRequest(
            context, InferenceCapability.BALANCED, InferenceModelProfile.balanced(), "System prompt", "{}"
        );

        Optional<InvestigationNarrative> result = client.generateNarrative(request);

        assertThat(result).isEmpty();
        mockServer.verify();
    }
}
