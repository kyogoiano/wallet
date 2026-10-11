package br.com.wallet.integration.fraud.investigation;

import br.com.wallet.fraud.investigation.api.model.AtomicEvidenceItem;
import br.com.wallet.fraud.investigation.api.model.FraudArchetype;
import br.com.wallet.fraud.investigation.api.model.FraudRiskSnapshot;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.api.model.RecommendedAction;
import br.com.wallet.fraud.investigation.api.model.RiskClassification;
import br.com.wallet.fraud.investigation.internal.grounding.ClaimGroundingValidator;
import br.com.wallet.fraud.investigation.internal.inference.OllamaInferenceClient;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.fraud.investigation.spi.InferenceModelProfile;
import br.com.wallet.fraud.investigation.spi.StructuredInferenceRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration-slm")
@DisplayName("OllamaInferenceClient Integration Tests (Testcontainers & Real SLM Runtime REQ-VEC-012 / I-VEC-010)")
public class OllamaInferenceClientIT {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OllamaInferenceClientIT.class);

    private static final DockerImageName OLLAMA_IMAGE = DockerImageName.parse("ollama/ollama:latest");
    private static GenericContainer<?> ollamaContainer;

    private ClaimGroundingValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new ClaimGroundingValidator();
    }

    @AfterAll
    static void tearDownAll() {
        if (ollamaContainer != null && ollamaContainer.isRunning()) {
            ollamaContainer.stop();
            ollamaContainer = null;
        }
    }

    @Test
    @DisplayName("I-VEC-009: Should gracefully degrade to Optional.empty() when Ollama endpoint is offline")
    void shouldGracefullyDegradeWhenOffline() {
        log.info("[OllamaInferenceClientIT] Testing I-VEC-009: intentionally triggering connection refusal on offline port 59999...");
        RestClient.Builder builder = RestClient.builder();
        OllamaInferenceClient offlineClient = new OllamaInferenceClient(
            builder,
            "http://localhost:59999",
            objectMapper
        );

        SanitizedInferenceContext context = createSampleContext();
        StructuredInferenceRequest request = new StructuredInferenceRequest(
            context,
            InferenceCapability.FAST,
            InferenceModelProfile.fast(),
            "You are a financial fraud analyst.",
            "{}"
        );

        Optional<InvestigationNarrative> result = offlineClient.generateNarrative(request);
        assertThat(result).isEmpty();
        log.info("[OllamaInferenceClientIT] I-VEC-009 degradation passed: returned Optional.empty() as expected.");
    }

    @Test
    @DisplayName("REQ-VEC-012 & I-VEC-010: Should communicate with Ollama runtime and generate grounded narrative")
    void shouldCommunicateWithOllamaRuntime() {
        StructuredInferenceRequest request = createInferenceRequest();
        String targetModel = request.profile().modelName();
        log.info("[OllamaInferenceClientIT] Testing REQ-VEC-012 / I-VEC-010 with target model: {}", targetModel);

        String baseUrl = resolveOllamaBaseUrl(targetModel);

        RestClient.Builder builder = RestClient.builder();
        OllamaInferenceClient client = new OllamaInferenceClient(builder, baseUrl, objectMapper);

        Assumptions.assumeTrue(
            isModelPresentInTags(baseUrl, targetModel),
            "Model " + targetModel + " is not available in Ollama runtime; skipping real inference verification"
        );

        Optional<InvestigationNarrative> result = client.generateNarrative(request);
        assertThat(result).isPresent();

        InvestigationNarrative narrative = result.get();
        assertThat(narrative.executiveSummary()).isNotBlank();
        InvestigationEvidence rawEvidence = createRawEvidence();
        boolean valid = validator.isValid(narrative, rawEvidence);
        assertThat(valid).isTrue();

        log.info("[OllamaInferenceClientIT] Real SLM container inference verified: executiveSummary='{}', claims={}",
            narrative.executiveSummary(), narrative.claims().size());
    }

    private String resolveOllamaBaseUrl(String modelName) {
        // 1. Check if a local Ollama instance is already running (e.g. host machine or docker-compose)
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/tags"))
                .timeout(Duration.ofMillis(500))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ensureModelAvailableHttp("http://localhost:11434", modelName);
                return "http://localhost:11434";
            }
        } catch (Exception ignored) {
            // Local Ollama not running, fallback to Testcontainers
        }

        // 2. Start Testcontainers Ollama container if Docker is available
        try {
            if (ollamaContainer == null) {
                ollamaContainer = new GenericContainer<>(OLLAMA_IMAGE)
                    .withExposedPorts(11434)
                    .waitingFor(Wait.forHttp("/api/tags").forPort(11434).forStatusCode(200))
                    .withStartupTimeout(Duration.ofSeconds(120));
                ollamaContainer.start();
            }
            String containerUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
            ensureModelAvailableContainer(ollamaContainer, containerUrl, modelName);
            return containerUrl;
        } catch (Exception e) {
            Assumptions.abort("Docker or Ollama container not available in this environment: " + e.getMessage());
            return "http://localhost:11434";
        }
    }

    private static void ensureModelAvailableContainer(GenericContainer<?> container, String baseUrl, String modelName) {
        try {
            if (isModelPresentInTags(baseUrl, modelName)) {
                return;
            }

            // Pull model via container CLI
            org.testcontainers.containers.Container.ExecResult execResult =
                container.execInContainer("ollama", "pull", modelName);
            if (execResult.getExitCode() != 0) {
                // Fallback: trigger pull via Ollama HTTP API
                ensureModelAvailableHttp(baseUrl, modelName);
            }
        } catch (Exception e) {
            ensureModelAvailableHttp(baseUrl, modelName);
        }
    }

    private static void ensureModelAvailableHttp(String baseUrl, String modelName) {
        try {
            if (isModelPresentInTags(baseUrl, modelName)) {
                return;
            }

            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest pullReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/pull"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\": \"" + modelName + "\", \"stream\": false}"))
                .build();
            httpClient.send(pullReq, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
        }
    }

    private static boolean isModelPresentInTags(String baseUrl, String modelName) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body() != null && response.body().contains(modelName);
        } catch (Exception e) {
            return false;
        }
    }

    private StructuredInferenceRequest createInferenceRequest() {
        SanitizedInferenceContext context = createSampleContext();
        String systemPrompt = """
            You are a financial fraud investigation synthesizer and analyst copilot.
            Synthesize the findings into structured JSON strictly following this schema:
            {
              "executiveSummary": "string",
              "claims": [
                {
                  "type": "ARCHETYPE_SIMILARITY",
                  "summary": "string",
                  "evidenceReferences": ["GRAPH-001"]
                }
              ],
              "actionRationale": "string"
            }
            Every claim MUST reference a valid evidence ID from the context (e.g. GRAPH-001 or TEMPORAL-014).
            """;

        return new StructuredInferenceRequest(
            context,
            InferenceCapability.BALANCED,
            InferenceModelProfile.balanced(),
            systemPrompt,
            "{}"
        );
    }

    private SanitizedInferenceContext createSampleContext() {
        FraudRiskSnapshot risks = new FraudRiskSnapshot(0.7, 0.8, 0.6, 0.75, 1.85, FraudArchetype.MONEY_MULE_RAPID_DRAIN, 0.88);
        return new SanitizedInferenceContext(
            "MASK_USER_TARGET",
            risks,
            List.of(
                new AtomicEvidenceItem("GRAPH-001", "SHARED_INFRASTRUCTURE", "MASK_USER_TARGET", Map.of("sharedCount", 4)),
                new AtomicEvidenceItem("TEMPORAL-014", "RAPID_FUND_MOVEMENT", "MASK_USER_TARGET", Map.of("windowHours", 2))
            ),
            RiskClassification.HIGH,
            List.of(RecommendedAction.MANUAL_REVIEW, RecommendedAction.TEMPORARY_OUTGOING_RESTRICTION)
        );
    }

    private InvestigationEvidence createRawEvidence() {
        return new InvestigationEvidence(
            new FraudRiskSnapshot(0.7, 0.8, 0.6, 0.75, 1.85, FraudArchetype.MONEY_MULE_RAPID_DRAIN, 0.88),
            List.of(
                new AtomicEvidenceItem("GRAPH-001", "SHARED_INFRASTRUCTURE", "MASK_USER_TARGET", Map.of("sharedCount", 4)),
                new AtomicEvidenceItem("TEMPORAL-014", "RAPID_FUND_MOVEMENT", "MASK_USER_TARGET", Map.of("windowHours", 2))
            )
        );
    }
}
