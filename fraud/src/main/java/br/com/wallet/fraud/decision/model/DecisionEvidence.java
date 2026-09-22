package br.com.wallet.fraud.decision.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable, machine-verifiable evidence record grounded via canonical SHA-256 content hashing (I-TYPED-004).
 *
 * @param evidenceKey logical identifier for the evidence snapshot
 * @param summary human-readable summary
 * @param facts structured dictionary of factual signals
 * @param contentHash SHA-256 digest of canonicalized facts
 */
public record DecisionEvidence(
    String evidenceKey,
    String summary,
    Map<String, Object> facts,
    String contentHash
) {

    public DecisionEvidence {
        Objects.requireNonNull(evidenceKey, "evidenceKey must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(facts, "facts must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        facts = Map.copyOf(facts);
    }

    /**
     * Factory computing the canonical JSON SHA-256 hash over facts.
     */
    public static DecisionEvidence of(final String evidenceKey, final String summary, final Map<String, Object> facts) {
        Objects.requireNonNull(evidenceKey, "evidenceKey must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(facts, "facts must not be null");

        String canonical = canonicalize(facts);
        String hash = sha256Hex(canonical);
        return new DecisionEvidence(evidenceKey, summary, facts, hash);
    }

    private static String canonicalize(final Map<String, Object> facts) {
        TreeMap<String, Object> sorted = new TreeMap<>(facts);
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String s) {
                sb.append("\"").append(s).append("\"");
            } else {
                sb.append(val);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String sha256Hex(final String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
