package br.com.wallet.core.security;

/**
 * Standard HTTP header and protocol constants for HMAC perimeter authentication
 * and multi-tenant security boundaries.
 *
 * <p><b>Single Financial Idempotency Identity Rule:</b>
 * Exactly one financial idempotency identity exists throughout the architecture:
 * <ul>
 *   <li>HTTP Header: {@link #IDEMPOTENCY_KEY} ("Idempotency-Key")</li>
 *   <li>Canonical HMAC Field: {@code OPERATION-ID} (value of the "Idempotency-Key" header)</li>
 *   <li>Domain &amp; NATS Messaging Field: {@code operationId}</li>
 * </ul>
 */
public final class SecurityHeaders {

    /**
     * Client credential key identifier header.
     */
    public static final String X_KEY_ID = "X-Key-Id";

    /**
     * Request creation epoch millisecond timestamp header.
     */
    public static final String X_TIMESTAMP = "X-Timestamp";

    /**
     * Canonical request HMAC-SHA256 signature in lowercase hexadecimal format.
     */
    public static final String X_SIGNATURE = "X-Signature";

    /**
     * Unique client idempotency key header identifying the financial operation.
     */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /**
     * Canonical protocol version identifier used in HMAC signature construction.
     */
    public static final String PROTOCOL_VERSION = "WALLET-HMAC-V1";

    private SecurityHeaders() {
        // Prevent instantiation
    }
}
