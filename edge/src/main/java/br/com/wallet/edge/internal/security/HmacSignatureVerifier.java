package br.com.wallet.edge.internal.security;

import br.com.wallet.edge.api.CredentialMaterial;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Constant-time HMAC-SHA256 signature verifier (I-SEC-002, REQ-SEC-002, TASK-SEC-2.3).
 */
public class HmacSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIGNATURE_HEX_LENGTH = 64;

    /**
     * Verifies whether the provided client signature matches the HMAC of the canonical request.
     * Verification is performed in constant time using MessageDigest.isEqual.
     *
     * @param canonicalRequest the canonicalized HTTP request string
     * @param clientSignature  the client-supplied hexadecimal signature
     * @param material         the cryptographic secret key material
     * @return true if valid, false otherwise
     */
    public boolean verify(
            @Nullable String canonicalRequest,
            @Nullable String clientSignature,
            @Nullable CredentialMaterial material
    ) {
        if (canonicalRequest == null || clientSignature == null || material == null) {
            return false;
        }

        String normalizedSig = clientSignature.trim();
        if (normalizedSig.length() != SIGNATURE_HEX_LENGTH) {
            return false;
        }

        byte[] clientBytes;
        try {
            clientBytes = HexFormat.of().parseHex(normalizedSig);
        } catch (IllegalArgumentException e) {
            // Not a valid hexadecimal string
            return false;
        }

        byte[] expectedBytes = computeHmacBytes(canonicalRequest, material.secret());
        return MessageDigest.isEqual(expectedBytes, clientBytes);
    }

    /**
     * Computes the lowercase hexadecimal HMAC-SHA256 signature for a string using the given key material.
     */
    public String computeSignatureHex(String data, CredentialMaterial material) {
        byte[] hmacBytes = computeHmacBytes(data, material.secret());
        return HexFormat.of().formatHex(hmacBytes);
    }

    private byte[] computeHmacBytes(String data, byte[] secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }
}
