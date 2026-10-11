package br.com.wallet.unit.dlq;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.TenantMismatchException;
import br.com.wallet.dlq.api.FailureClassifier;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.ledger.api.exceptions.InsufficientFundsException;
import br.com.wallet.ledger.api.exceptions.PermanentException;
import br.com.wallet.ledger.api.exceptions.TransientException;
import br.com.wallet.security.envelope.KeyId;
import br.com.wallet.security.envelope.OperationId;
import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.failure.CryptographicIntegrityException;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.failure.MalformedEnvelopeException;
import br.com.wallet.security.failure.ReplayDetectedException;
import br.com.wallet.security.replay.PrincipalId;
import br.com.wallet.security.replay.ReplayKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FailureClassifier Unit Tests (REQ-TDLQ-001)")
class FailureClassifierTest {

    private static final TenantId TENANT_ID = new TenantId("tenant-alpha");
    private static final KeyId KEY_ID = new KeyId("key-1");
    private static final OperationId OPERATION_ID = new OperationId(UUID.randomUUID());
    private static final PrincipalId PRINCIPAL_ID = new PrincipalId("principal-1");
    private static final ReplayKey REPLAY_KEY = new ReplayKey(TENANT_ID, PRINCIPAL_ID, "nonce-123");

    @Test
    @DisplayName("Should classify transient exceptions as TRANSIENT")
    void shouldClassifyTransientExceptions() {
        assertThat(FailureClassifier.classify(new TransientException("DB deadlock detected")))
                .isEqualTo(DlqFailureType.TRANSIENT);
        assertThat(FailureClassifier.classify(new TimeoutException("NATS ack timeout")))
                .isEqualTo(DlqFailureType.TRANSIENT);
        assertThat(FailureClassifier.classify(new SocketTimeoutException("Read timed out")))
                .isEqualTo(DlqFailureType.TRANSIENT);
        assertThat(FailureClassifier.classify(new ConnectException("Connection refused")))
                .isEqualTo(DlqFailureType.TRANSIENT);
        assertThat(FailureClassifier.classify(new SQLException("connection pool starvation", "08001")))
                .isEqualTo(DlqFailureType.TRANSIENT);
        assertThat(FailureClassifier.classify(new KeyManagementUnavailableException("KMS timeout", TENANT_ID, KEY_ID, new TimeoutException())))
                .isEqualTo(DlqFailureType.TRANSIENT);
    }

    @Test
    @DisplayName("Should classify permanent business rejections as PERMANENT")
    void shouldClassifyPermanentExceptions() {
        assertThat(FailureClassifier.classify(new AccountBlockedException("Account is blocked")))
                .isEqualTo(DlqFailureType.PERMANENT);
        assertThat(FailureClassifier.classify(new InsufficientFundsException("Insufficient funds for transfer")))
                .isEqualTo(DlqFailureType.PERMANENT);
        assertThat(FailureClassifier.classify(new PermanentException("Account does not exist")))
                .isEqualTo(DlqFailureType.PERMANENT);
        assertThat(FailureClassifier.classify(new FraudBlockedException(UUID.randomUUID(), UUID.randomUUID())))
                .isEqualTo(DlqFailureType.PERMANENT);
        assertThat(FailureClassifier.classify(new TenantMismatchException("Cross-tenant violation")))
                .isEqualTo(DlqFailureType.PERMANENT);
    }

    @Test
    @DisplayName("Should classify malformed payloads and envelope formatting errors as POISON")
    void shouldClassifyPoisonExceptions() {
        assertThat(FailureClassifier.classify(new MalformedEnvelopeException("Invalid framing")))
                .isEqualTo(DlqFailureType.POISON);
        assertThat(FailureClassifier.classify(new IllegalArgumentException("Missing mandatory transfer fields")))
                .isEqualTo(DlqFailureType.POISON);
    }

    @Test
    @DisplayName("Should classify cryptographic and replay integrity violations as SECURITY")
    void shouldClassifySecurityExceptions() {
        assertThat(FailureClassifier.classify(new CryptographicIntegrityException("AEAD tag mismatch", OPERATION_ID)))
                .isEqualTo(DlqFailureType.SECURITY);
        assertThat(FailureClassifier.classify(new ReplayDetectedException("Nonce reused", REPLAY_KEY)))
                .isEqualTo(DlqFailureType.SECURITY);
        assertThat(FailureClassifier.classify(new SecurityException("HMAC key invalid")))
                .isEqualTo(DlqFailureType.SECURITY);
    }

    @Test
    @DisplayName("Should unwrap nested causes to classify underlying root cause")
    void shouldUnwrapNestedCauses() {
        RuntimeException wrapped = new RuntimeException("Wrapped exception",
                new AccountBlockedException("Account is blocked"));
        assertThat(FailureClassifier.classify(wrapped)).isEqualTo(DlqFailureType.PERMANENT);

        RuntimeException wrappedCrypto = new RuntimeException("Outer wrapper",
                new CryptographicIntegrityException("AEAD tag mismatch", OPERATION_ID));
        assertThat(FailureClassifier.classify(wrappedCrypto)).isEqualTo(DlqFailureType.SECURITY);

        RuntimeException wrappedKms = new RuntimeException("Outer wrapper",
                new KeyManagementUnavailableException("KMS unavailable", TENANT_ID, KEY_ID, new ConnectException()));
        assertThat(FailureClassifier.classify(wrappedKms)).isEqualTo(DlqFailureType.TRANSIENT);
    }

    @Test
    @DisplayName("Should return POISON as safe fallback for null or unknown unclassified throwables")
    void shouldFallbackToPoisonOnUnknown() {
        assertThat(FailureClassifier.classify(null)).isEqualTo(DlqFailureType.POISON);
        assertThat(FailureClassifier.classify(new Exception("Generic unclassified failure")))
                .isEqualTo(DlqFailureType.POISON);
    }
}
