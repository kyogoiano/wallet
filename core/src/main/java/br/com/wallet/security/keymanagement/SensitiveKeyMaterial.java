package br.com.wallet.security.keymanagement;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AutoCloseable container for sensitive cryptographic key material that enforces
 * immediate memory zeroization upon closure (REQ-SEC-024, I-SEC-016).
 */
public final class SensitiveKeyMaterial implements AutoCloseable {

    private final byte[] material;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public SensitiveKeyMaterial(byte[] rawKey) {
        Objects.requireNonNull(rawKey, "rawKey must not be null");
        this.material = rawKey.clone();
    }

    /**
     * Returns a defensive clone of the underlying key bytes.
     *
     * @throws IllegalStateException if the key material has been destroyed.
     */
    public byte[] getEncoded() {
        if (destroyed.get()) {
            throw new IllegalStateException("Key material destroyed");
        }
        return material.clone();
    }

    /**
     * Returns the length in bytes of the key material.
     */
    public int length() {
        if (destroyed.get()) {
            throw new IllegalStateException("Key material destroyed");
        }
        return material.length;
    }

    /**
     * Checks if this key material has been destroyed (zeroized).
     */
    public boolean isDestroyed() {
        return destroyed.get();
    }

    /**
     * Zeroizes the underlying memory buffer and marks this container as destroyed.
     */
    @Override
    public void close() {
        if (destroyed.compareAndSet(false, true)) {
            Arrays.fill(material, (byte) 0);
        }
    }

    @Override
    public String toString() {
        return "SensitiveKeyMaterial[length=" + material.length + ", destroyed=" + destroyed.get() + "]";
    }
}
