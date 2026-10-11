package br.com.wallet.edge.internal.ingress;

import java.nio.charset.StandardCharsets;

/**
 * Gate enforcing maximum command envelope size (default 64KB).
 * Mandated by REQ-EDG-015. Requests exceeding this limit must be rejected with HTTP 413 Payload Too Large.
 */
public record EdgeRequestValidator(int maxPayloadBytes) {

    public static final int MAX_COMMAND_PAYLOAD_BYTES = 64 * 1024; // 64KB

    public EdgeRequestValidator() {
        this(MAX_COMMAND_PAYLOAD_BYTES);
    }

    public boolean isPayloadValid(String payloadJson) {
        if (payloadJson == null) return false;
        int byteCount = payloadJson.getBytes(StandardCharsets.UTF_8).length;
        return byteCount <= maxPayloadBytes;
    }

    public int getPayloadByteCount(String payloadJson) {
        if (payloadJson == null) return 0;
        return payloadJson.getBytes(StandardCharsets.UTF_8).length;
    }
}
