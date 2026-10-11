package br.com.wallet.unit.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.internal.propagation.MultiPathAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("MultiPathAggregator Unit Tests (Probabilistic Union & Sub-linear Saturation)")
class MultiPathAggregatorTest {

    private MultiPathAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new MultiPathAggregator();
    }

    @Test
    @DisplayName("REQ-PROP-004: Should aggregate two independent paths using probabilistic union without double-counting")
    void shouldAggregateProbabilisticUnionWithoutDoubleCounting() {
        // Path 1 = 0.50, Path 2 = 0.40
        // R = 1 - (1 - 0.50) * (1 - 0.40) = 1 - (0.50 * 0.60) = 0.70
        double aggregated = aggregator.aggregatePathInfluences(List.of(0.50, 0.40));
        assertThat(aggregated).isCloseTo(0.70, within(0.0001));
    }

    @Test
    @DisplayName("REQ-PROP-004: Should aggregate three independent paths correctly")
    void shouldAggregateThreePaths() {
        // Paths: 0.50, 0.30, 0.20
        // R = 1 - (0.50 * 0.70 * 0.80) = 1 - 0.28 = 0.72
        double aggregated = aggregator.aggregatePathInfluences(List.of(0.50, 0.30, 0.20));
        assertThat(aggregated).isCloseTo(0.72, within(0.0001));
    }

    @Test
    @DisplayName("I-PROP-001: Should return 0.0 for empty path list")
    void shouldReturnZeroForEmptyPaths() {
        double aggregated = aggregator.aggregatePathInfluences(Collections.emptyList());
        assertThat(aggregated).isEqualTo(0.0);
    }

    @Test
    @DisplayName("I-PROP-001: Should return single path influence if only one path exists")
    void shouldReturnSinglePathInfluence() {
        double aggregated = aggregator.aggregatePathInfluences(List.of(0.45));
        assertThat(aggregated).isCloseTo(0.45, within(0.0001));
    }

    @Test
    @DisplayName("I-PROP-001 & I-PROP-003: Sub-linear saturation should never exceed 1.0 even with many high-influence paths")
    void shouldSublinearlySaturateWithMultiplePaths() {
        List<Double> manyPaths = List.of(0.90, 0.90, 0.90, 0.90, 0.90);
        // R = 1 - (0.1^5) = 0.99999
        double aggregated = aggregator.aggregatePathInfluences(manyPaths);
        
        assertThat(aggregated).isLessThan(1.0);
        assertThat(aggregated).isGreaterThan(0.9999);
    }

    @Test
    @DisplayName("I-PROP-001: Should clamp edge values properly within [0.0, 1.0]")
    void shouldGuaranteeBoundsWithinZeroAndOne() {
        double zero = aggregator.aggregatePathInfluences(List.of(0.0, 0.0));
        assertThat(zero).isEqualTo(0.0);

        double maxOne = aggregator.aggregatePathInfluences(List.of(1.0, 0.5));
        assertThat(maxOne).isEqualTo(1.0);
    }
}
