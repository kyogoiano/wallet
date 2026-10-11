package br.com.wallet.security.telemetry;

import br.com.wallet.security.envelope.CryptoEnvelope;
import br.com.wallet.security.envelope.EnvelopeVersion;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;

import java.util.Objects;

/**
 * Safe diagnostic summary of a cryptographic envelope for telemetry, OpenTelemetry spans,
 * and audit logs, strictly omitting all sensitive plaintext and key material (REQ-SEC-030, REQ-SEC-031, I-SEC-012).
 */
public record CryptoEnvelopeSummary(
        EnvelopeVersion version,
        TenantId tenantId,
        OperationId operationId,
        KeyId keyId,
        int ciphertextLength
) {
    public CryptoEnvelopeSummary {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public static CryptoEnvelopeSummary fromEnvelope(CryptoEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        return new CryptoEnvelopeSummary(
                envelope.version(),
                envelope.tenantId(),
                envelope.operationId(),
                envelope.keyId(),
                envelope.ciphertext().length()
        );
    }

    @Override
    public String toString() {
        return "CryptoEnvelopeSummary[version=" + version +
                ", tenantId=" + tenantId +
                ", operationId=" + operationId +
                ", keyId=" + keyId +
                ", ciphertextLength=" + ciphertextLength + "]";
    }
}
