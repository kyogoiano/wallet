package br.com.wallet.security.keymanagement;

import br.com.wallet.security.envelope.CryptoBytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TASK-10.3: SensitiveKeyMaterial & GeneratedDataKey Zeroization Test (REQ-SEC-024, I-SEC-016)")
class SensitiveKeyMaterialTest {

    @Test
    @DisplayName("Assert SensitiveKeyMaterial defensively copies raw key bytes on construction")
    void shouldDefensivelyCopyRawKey() {
        byte[] rawKey = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        try (SensitiveKeyMaterial keyMaterial = new SensitiveKeyMaterial(rawKey)) {
            rawKey[0] = 99; // Mutate source
            assertThat(keyMaterial.getEncoded()[0]).isEqualTo((byte) 1);
        }
    }

    @Test
    @DisplayName("Assert getEncoded returns defensive clone of key material")
    void shouldReturnDefensiveCloneOnGetEncoded() {
        byte[] rawKey = new byte[]{1, 2, 3, 4};
        try (SensitiveKeyMaterial keyMaterial = new SensitiveKeyMaterial(rawKey)) {
            byte[] encoded = keyMaterial.getEncoded();
            encoded[0] = 77; // Mutate returned
            assertThat(keyMaterial.getEncoded()[0]).isEqualTo((byte) 1);
        }
    }

    @Test
    @DisplayName("Assert close zeroizes memory and causes getEncoded to throw IllegalStateException")
    void shouldZeroizeAndThrowAfterClose() {
        byte[] rawKey = new byte[]{10, 20, 30, 40};
        SensitiveKeyMaterial keyMaterial = new SensitiveKeyMaterial(rawKey);

        assertThat(keyMaterial.isDestroyed()).isFalse();
        assertThat(keyMaterial.getEncoded()).containsExactly((byte) 10, (byte) 20, (byte) 30, (byte) 40);

        keyMaterial.close();

        assertThat(keyMaterial.isDestroyed()).isTrue();
        assertThatThrownBy(keyMaterial::getEncoded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Key material destroyed");
    }

    @Test
    @DisplayName("Assert repeated close calls are idempotent")
    void shouldHandleRepeatedCloseCallsIdempotently() {
        SensitiveKeyMaterial keyMaterial = new SensitiveKeyMaterial(new byte[]{1, 2, 3});
        keyMaterial.close();
        keyMaterial.close();
        assertThat(keyMaterial.isDestroyed()).isTrue();
    }

    @Test
    @DisplayName("Assert GeneratedDataKey auto-close closes underlying plaintextDek")
    void shouldClosePlaintextDekViaGeneratedDataKey() {
        byte[] rawPlaintext = new byte[]{1, 2, 3, 4};
        byte[] rawWrapped = new byte[]{9, 8, 7, 6};

        SensitiveKeyMaterial plaintextDek = new SensitiveKeyMaterial(rawPlaintext);
        CryptoBytes wrappedDek = new CryptoBytes(rawWrapped);

        try (GeneratedDataKey dataKey = new GeneratedDataKey(plaintextDek, wrappedDek)) {
            assertThat(dataKey.plaintextDek().isDestroyed()).isFalse();
            assertThat(dataKey.wrappedDek().length()).isEqualTo(4);
        }

        assertThat(plaintextDek.isDestroyed()).isTrue();
        assertThatThrownBy(plaintextDek::getEncoded)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Assert toString does not leak key bytes")
    void shouldNotLeakKeyBytesInToString() {
        SensitiveKeyMaterial keyMaterial = new SensitiveKeyMaterial(new byte[]{77, 88, 99});
        assertThat(keyMaterial.toString()).doesNotContain("77", "88", "99");
        assertThat(keyMaterial.toString()).contains("length=3");
    }
}
