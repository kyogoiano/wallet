package br.com.wallet.fraud.investigation.internal.inference;

import br.com.wallet.fraud.investigation.api.model.ClaimType;
import br.com.wallet.fraud.investigation.api.model.InvestigationClaim;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.spi.LocalInferenceClient;
import br.com.wallet.fraud.investigation.spi.StructuredInferenceRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Air-gapped local inference client communicating with Ollama `/api/chat` endpoint (I-VEC-004).
 * Uses Spring RestClient with structured JSON output and graceful degradation.
 */
@Component
public class OllamaInferenceClient implements LocalInferenceClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaInferenceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OllamaInferenceClient(
        final RestClient.@NonNull Builder restClientBuilder,
        @Value("${fraud.investigation.ollama.base-url:http://localhost:11434}") final String baseUrl,
        @NonNull final ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(restClientBuilder, "restClientBuilder cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    @NonNull
    public Optional<InvestigationNarrative> generateNarrative(@NonNull final StructuredInferenceRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        try {
            String contextJson = objectMapper.writeValueAsString(request.context());

            Map<String, Object> payload = Map.of(
                "model", request.profile().modelName(),
                "messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", "Synthesize findings for context: " + contextJson)
                ),
                "format", "json",
                "stream", false,
                "options", Map.of(
                    "temperature", request.profile().temperature(),
                    "num_predict", request.profile().maxTokens()
                )
            );

            String payloadJson = objectMapper.writeValueAsString(payload);

            String responseBody = restClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadJson)
                .retrieve()
                .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("Empty response received from Ollama endpoint");
                return Optional.empty();
            }

            return parseNarrative(responseBody);
        } catch (RestClientException e) {
            log.warn("Local Ollama inference unavailable or timed out: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to execute local Ollama inference: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @NonNull
    private Optional<InvestigationNarrative> parseNarrative(@NonNull final String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageNode = root.path("message");
            String content = messageNode.path("content").asString();

            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            JsonNode contentNode = objectMapper.readTree(content);
            String executiveSummary = contentNode.path("executiveSummary").asString("");
            String actionRationale = contentNode.path("actionRationale").asString("");

            List<InvestigationClaim> claims = new ArrayList<>();
            JsonNode claimsArray = contentNode.path("claims");
            if (claimsArray.isArray()) {
                for (JsonNode claimNode : claimsArray) {
                    String typeStr = claimNode.path("type").asString("ARCHETYPE_SIMILARITY");
                    ClaimType type;
                    try {
                        type = ClaimType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        type = ClaimType.ARCHETYPE_SIMILARITY;
                    }

                    String summary = claimNode.path("summary").asString("");
                    List<String> refs = new ArrayList<>();
                    JsonNode refsArray = claimNode.path("evidenceReferences");
                    if (refsArray.isArray()) {
                        for (JsonNode ref : refsArray) {
                            refs.add(ref.asString());
                        }
                    }
                    claims.add(new InvestigationClaim(type, summary, refs));
                }
            }

            return Optional.of(new InvestigationNarrative(executiveSummary, claims, actionRationale));
        } catch (JacksonException e) {
            log.warn("Failed to parse narrative JSON from Ollama output: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
