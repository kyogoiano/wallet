package br.com.wallet.security.keymanagement;

import br.com.wallet.security.envelope.CryptoBytes;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * AutoCloseable container holding an active plaintext data encryption key (DEK) and its
 * KMS-wrapped opaque counterpart (REQ-SEC-024, I-SEC-016).
 */
public record GeneratedDataKey(
        SensitiveKeyMaterial plaintextDek,
        CryptoBytes wrappedDek
) implements AutoCloseable {

    public GeneratedDataKey {
        Objects.requireNonNull(plaintextDek, "plaintextDek must not be null");
        Objects.requireNonNull(wrappedDek, "wrappedDek must not be null");
    }

    @Override
    public void close() {
        if (plaintextDek != null) {
            plaintextDek.close();
        }
    }

    @Override
    public @NonNull String toString() {
        return "GeneratedDataKey[plaintext=" + plaintextDek + ", wrapped=" + wrappedDek + "]";
    }
}
