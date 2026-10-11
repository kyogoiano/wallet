package br.com.wallet.edge.internal.security;

import br.com.wallet.core.security.SecurityHeaders;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Deterministic HTTP request canonicalizer for WALLET-HMAC-V1 (REQ-SEC-002, TASK-SEC-2.3).
 *
 * <p>Canonical format:
 * <pre>
 * WALLET-HMAC-V1\n
 * HTTP_METHOD\n
 * CANONICAL_PATH\n
 * CANONICAL_QUERY\n
 * KEY_ID\n
 * TIMESTAMP\n
 * OPERATION_ID\n
 * HEX_BODY_SHA256
 * </pre>
 * Without trailing newline.
 */
public final class HmacCanonicalizer {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final String EMPTY_SHA256_HEX =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private HmacCanonicalizer() {
        // utility class
    }

    /**
     * Builds the deterministic canonical request string.
     */
    public static String buildCanonicalRequest(
            @Nullable String method,
            @Nullable String uriPath,
            @Nullable String queryString,
            @Nullable String keyId,
            @Nullable String timestamp,
            @Nullable String operationId,
            byte @Nullable [] body
    ) {
        String normalizedMethod = method != null ? method.trim().toUpperCase(Locale.ROOT) : "POST";
        String normalizedPath = normalizePath(uriPath);
        String canonicalQuery = canonicalizeQuery(queryString);
        String normalizedKeyId = keyId != null ? keyId.trim() : "";
        String normalizedTimestamp = timestamp != null ? timestamp.trim() : "";
        String normalizedOpId = operationId != null ? operationId.trim() : "";
        String bodyHashHex = sha256Hex(body);

        return SecurityHeaders.PROTOCOL_VERSION + "\n" +
                normalizedMethod + "\n" +
                normalizedPath + "\n" +
                canonicalQuery + "\n" +
                normalizedKeyId + "\n" +
                normalizedTimestamp + "\n" +
                normalizedOpId + "\n" +
                bodyHashHex;
    }

    /**
     * Computes lowercase hexadecimal SHA-256 of byte array.
     */
    public static String sha256Hex(byte @Nullable [] data) {
        if (data == null || data.length == 0) {
            return EMPTY_SHA256_HEX;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static String normalizePath(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = URI.create(path.trim()).normalize().getPath();
        if (normalized.isEmpty()) {
            return "/";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        // Remove trailing slash unless path is just "/"
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String canonicalizeQuery(@Nullable String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        String[] pairs = queryString.trim().split("&");
        Arrays.sort(pairs);
        StringBuilder sb = new StringBuilder();
        for (final String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append(pair);
        }
        return sb.toString();
    }
}
