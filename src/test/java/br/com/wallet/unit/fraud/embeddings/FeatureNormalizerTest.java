package br.com.wallet.unit.fraud.embeddings;

import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.normalization.FeatureNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("FeatureNormalizer Unit Tests (L2 Unit Vector, Magnitude Preservation & Zero-Activity Neutrality)")
class FeatureNormalizerTest {

    private FeatureNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new FeatureNormalizer();
    }

    @Test
    @DisplayName("I-VEC-001: Should compute unit L2 vector and preserve scalar magnitude")
    void shouldComputeUnitVectorAndPreserveMagnitude() {
        UUID entityId = UUID.randomUUID();
        // 3D slice for simplicity: [3.0, 4.0, 0, ..., 0] -> magnitude = 5.0, unit vector = [0.6, 0.8, 0, ...]
        double[] raw = new double[16];
        raw[0] = 0.3;
        raw[1] = 0.4;
        // magnitude = sqrt(0.09 + 0.16) = sqrt(0.25) = 0.5

        BehavioralFeatureVector vector = normalizer.normalize(entityId, raw, 10, new BigDecimal("1500.00"));

        assertThat(vector.entityId()).isEqualTo(entityId);
        assertThat(vector.featureMagnitude()).isCloseTo(0.5, within(0.0001));
        assertThat(vector.vector()[0]).isCloseTo(0.6, within(0.0001));
        assertThat(vector.vector()[1]).isCloseTo(0.8, within(0.0001));
        assertThat(vector.transactionCount()).isEqualTo(10);
        assertThat(vector.transactionVolume()).isEqualByComparingTo("1500.00");

        // Verify ||v||_2 == 1.0
        double norm = 0.0;
        for (double v : vector.vector()) {
            norm += v * v;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("I-VEC-008: Zero Activity Neutrality should return zero vector and zero magnitude when M = 0")
    void shouldReturnZeroVectorAndZeroMagnitudeWhenActivityIsZero() {
        UUID entityId = UUID.randomUUID();
        double[] rawZeros = new double[16];

        BehavioralFeatureVector vector = normalizer.normalize(entityId, rawZeros, 0, BigDecimal.ZERO);

        assertThat(vector.entityId()).isEqualTo(entityId);
        assertThat(vector.featureMagnitude()).isEqualTo(0.0);
        assertThat(vector.transactionCount()).isEqualTo(0);
        assertThat(vector.transactionVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        for (double val : vector.vector()) {
            assertThat(val).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("REQ-VEC-008: Should distinguish low and high intensity profiles with identical vector direction")
    void shouldDistinguishLowAndHighIntensityProfilesWithSameDirection() {
        UUID entityLow = UUID.randomUUID();
        UUID entityHigh = UUID.randomUUID();

        double[] rawLow = new double[16];
        double[] rawHigh = new double[16];
        for (int i = 0; i < 16; i++) {
            rawLow[i] = 0.1;
            rawHigh[i] = 0.8;
        }

        BehavioralFeatureVector vectorLow = normalizer.normalize(entityLow, rawLow, 5, new BigDecimal("500.00"));
        BehavioralFeatureVector vectorHigh = normalizer.normalize(entityHigh, rawHigh, 50, new BigDecimal("50000.00"));

        // Both vectors have identical directional components: 1/sqrt(16) = 1/4 = 0.25
        for (int i = 0; i < 16; i++) {
            assertThat(vectorLow.vector()[i]).isCloseTo(0.25, within(0.0001));
            assertThat(vectorHigh.vector()[i]).isCloseTo(0.25, within(0.0001));
        }

        // But magnitudes differ by 8x (0.4 vs 3.2), proving magnitude preserves intensity
        assertThat(vectorLow.featureMagnitude()).isCloseTo(0.4, within(0.0001));
        assertThat(vectorHigh.featureMagnitude()).isCloseTo(3.2, within(0.0001));
        assertThat(vectorHigh.featureMagnitude()).isGreaterThan(vectorLow.featureMagnitude() * 7);
    }
}
