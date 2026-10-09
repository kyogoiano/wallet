package br.com.wallet.copilot.internal.util;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

public final class ParametersHashUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ParametersHashUtil() {
    }

    @NonNull
    public static String computeHash(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON payload cannot be null or blank");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            StringBuilder canonical = new StringBuilder();
            canonicalize(root, canonical);
            byte[] hashBytes = sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to canonicalize and hash JSON payload: " + e.getMessage(), e);
        }
    }

    private static void canonicalize(JsonNode node, StringBuilder sb) {
        if (node.isObject()) {
            sb.append('{');
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            Collections.sort(keys);
            boolean first = true;
            for (final String key : keys) {
                JsonNode child = node.get(key);
                if (child == null || child.isNull()) {
                    continue; // omit null values for canonical form
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendQuoted(key, sb);
                sb.append(':');
                canonicalize(child, sb);
            }
            sb.append('}');
        } else if (node.isArray()) {
            sb.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                canonicalize(node.get(i), sb);
            }
            sb.append(']');
        } else if (node.isNumber()) {
            BigDecimal decimal = new BigDecimal(node.asText());
            if (decimal.compareTo(BigDecimal.ZERO) == 0) {
                sb.append('0');
            } else {
                sb.append(decimal.stripTrailingZeros().toPlainString());
            }
        } else if (node.isBoolean()) {
            sb.append(node.asBoolean());
        } else if (node.isTextual()) {
            appendQuoted(node.asText(), sb);
        } else if (node.isNull()) {
            sb.append("null");
        } else {
            sb.append(node.asText());
        }
    }

    private static void appendQuoted(String str, StringBuilder sb) {
        sb.append('"');
        sb.append(JsonStringEncoder.getInstance().quoteAsString(str));
        sb.append('"');
    }

    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
