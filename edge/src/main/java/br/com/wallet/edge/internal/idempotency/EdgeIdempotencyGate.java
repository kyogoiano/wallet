package br.com.wallet.edge.internal.idempotency;

import br.com.wallet.edge.api.CommandEnvelope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic Ingress Idempotency Gate (TASK-3.8, I-IDEMPOTENCY-001).
 * Computes canonical SHA-256 fingerprint over incoming JSON payload.
 * If the same operationId is submitted:
 * - With identical canonical payload -> IDEMPOTENT_REPLAY (202 Accepted)
 * - With different payload -> CONFLICT (HTTP 409 Conflict)
 */
public class EdgeIdempotencyGate {

    private final ConcurrentHashMap<UUID, String> fingerprintByOperation = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public EdgeIdempotencyGate() {
        this(new ObjectMapper());
    }

    public EdgeIdempotencyGate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public enum ValidationResult {
        NEW,
        IDEMPOTENT_REPLAY,
        CONFLICT
    }

    public ValidationResult validateAndRecord(CommandEnvelope command) {
        String canonicalHash = computeCanonicalHash(command.payloadJson());
        String existingHash = fingerprintByOperation.putIfAbsent(command.operationId(), canonicalHash);

        if (existingHash == null) {
            return ValidationResult.NEW;
        }
        if (existingHash.equals(canonicalHash)) {
            return ValidationResult.IDEMPOTENT_REPLAY;
        }
        return ValidationResult.CONFLICT;
    }

    public String computeCanonicalHash(String jsonPayload) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String canonical = toCanonicalString(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(jsonPayload.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(hash);
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 digest unavailable", ex);
            }
        }
    }

    private String toCanonicalString(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            List<String> fieldNames = new ArrayList<>(node.propertyNames());
            Collections.sort(fieldNames);
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < fieldNames.size(); i++) {
                String field = fieldNames.get(i);
                if (i > 0) sb.append(",");
                sb.append("\"").append(field).append("\":").append(toCanonicalString(node.get(field)));
            }
            sb.append("}");
            return sb.toString();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toCanonicalString(node.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return node.asString();
    }
}
