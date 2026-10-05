package br.com.wallet.dlq.api;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.core.exceptions.TenantMismatchException;
import br.com.wallet.dlq.api.model.DlqFailureType;
import br.com.wallet.ledger.api.exceptions.BusinessException;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.ledger.api.exceptions.PermanentException;
import br.com.wallet.ledger.api.exceptions.TransientException;
import br.com.wallet.security.failure.CryptographicIntegrityException;
import br.com.wallet.security.failure.KeyManagementUnavailableException;
import br.com.wallet.security.failure.MalformedEnvelopeException;
import br.com.wallet.security.failure.ReplayDetectedException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Deterministic classifier mapping raw exceptions to architectural DlqFailureType.
 * (REQ-TDLQ-001)
 */
public final class FailureClassifier {

    private FailureClassifier() {
    }

    /**
     * Inspects the throwable and its underlying causal chain to determine
     * the appropriate DlqFailureType.
     *
     * @param throwable raw exception caught during command ingestion or processing
     * @return non-null DlqFailureType
     */
    public static DlqFailureType classify(final Throwable throwable) {
        if (throwable == null) {
            return DlqFailureType.POISON;
        }

        Throwable current = throwable;
        final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (current != null && visited.add(current)) {
            final DlqFailureType direct = classifySingle(current);
            if (direct != null) {
                return direct;
            }
            current = current.getCause();
        }

        return DlqFailureType.POISON; // safe fallback
    }

    private static DlqFailureType classifySingle(final Throwable t) {
        if (t == null) {
            return null;
        }

        // 1. Security violations
        if (t instanceof CryptographicIntegrityException
                || t instanceof ReplayDetectedException
                || t instanceof SecurityException) {
            return DlqFailureType.SECURITY;
        }

        // 2. Transient infrastructure / lock contention / KMS timeouts
        if (t instanceof TransientException
                || t instanceof TimeoutException
                || t instanceof SocketTimeoutException
                || t instanceof ConnectException
                || t instanceof KeyManagementUnavailableException
                || t instanceof SQLException) {
            return DlqFailureType.TRANSIENT;
        }

        final String className = t.getClass().getName();
        if (className.contains("LockAcquisitionException")
                || className.contains("CannotAcquireLockException")
                || className.contains("PessimisticLockingFailureException")
                || className.contains("QueryTimeoutException")
                || className.contains("TransientDataAccessException")
                || className.contains("TransientException")) {
            return DlqFailureType.TRANSIENT;
        }

        // 3. Permanent business logic / account lifecycle rejections
        if (t instanceof AccountBlockedException
                || t instanceof PermanentException
                || t instanceof FraudBlockedException
                || t instanceof TenantMismatchException
                || t instanceof IdempotencyException
                || t instanceof BusinessException) {
            return DlqFailureType.PERMANENT;
        }
        if (className.contains("AccountBlockedException")
                || className.contains("InsufficientFundsException")
                || className.contains("PermanentException")
                || className.contains("BusinessException")) {
            return DlqFailureType.PERMANENT;
        }

        // 4. Poison messages / malformed envelopes / deserialization syntax errors
        if (t instanceof MalformedEnvelopeException
                || t instanceof IllegalArgumentException
                || t instanceof tools.jackson.core.JacksonException) {
            return DlqFailureType.POISON;
        }
        if (className.contains("JsonParseException")
                || className.contains("JsonProcessingException")
                || className.contains("MalformedEnvelopeException")
                || className.contains("DeserializationException")) {
            return DlqFailureType.POISON;
        }

        return null;
    }
}
