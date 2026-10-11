package br.com.wallet.unit.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.internal.propagation.PathInfluenceCalculator;
import br.com.wallet.fraud.intelligence.propagation.PropagationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("PathInfluenceCalculator Unit Tests (Edge Weights, Half-Life Decay & Multi-Hop Product)")
class PathInfluenceCalculatorTest {

    private PropagationConfig config;
    private PathInfluenceCalculator calculator;

    @BeforeEach
    void setUp() {
        config = PropagationConfig.defaultConfig();
        calculator = new PathInfluenceCalculator();
    }

    @Test
    @DisplayName("REQ-PROP-001: Should apply calibrated edge weights correctly across all relationship types")
    void shouldApplyCorrectEdgeWeights() {
        assertThat(config.getWeight(RelationshipType.OWNS)).isEqualTo(0.95);
        assertThat(config.getWeight(RelationshipType.SHARED_DEVICE)).isEqualTo(0.90);
        assertThat(config.getWeight(RelationshipType.SHARED_PHONE)).isEqualTo(0.85);
        assertThat(config.getWeight(RelationshipType.SHARED_EMAIL)).isEqualTo(0.75);
        assertThat(config.getWeight(RelationshipType.TRANSFERRED_TO)).isEqualTo(0.60);
        assertThat(config.getWeight(RelationshipType.USES)).isEqualTo(0.50);
        assertThat(config.getWeight(RelationshipType.SHARED_IP)).isEqualTo(0.35);
        assertThat(config.getWeight(RelationshipType.LOGGED_FROM)).isEqualTo(0.10);
        assertThat(config.getWeight(RelationshipType.SHARES)).isEqualTo(0.10);
    }

    @Test
    @DisplayName("REQ-PROP-002: Should decay by exactly 50% after one half-life (7 days)")
    void shouldDecayExponentiallyWithHalfLife() {
        Duration halfLife = Duration.ofDays(7);
        
        // 0 days elapsed -> decay factor 1.0
        double decay0d = calculator.calculateDecayFactor(Duration.ZERO, halfLife);
        assertThat(decay0d).isCloseTo(1.0, within(0.0001));

        // 7 days elapsed -> decay factor 0.50
        double decay7d = calculator.calculateDecayFactor(Duration.ofDays(7), halfLife);
        assertThat(decay7d).isCloseTo(0.50, within(0.0001));

        // 14 days elapsed -> decay factor 0.25
        double decay14d = calculator.calculateDecayFactor(Duration.ofDays(14), halfLife);
        assertThat(decay14d).isCloseTo(0.25, within(0.0001));

        // 21 days elapsed -> decay factor 0.125
        double decay21d = calculator.calculateDecayFactor(Duration.ofDays(21), halfLife);
        assertThat(decay21d).isCloseTo(0.125, within(0.0001));
    }

    @Test
    @DisplayName("REQ-PROP-002: Should compute elapsed time relative to deterministic as_of")
    void shouldDecayExponentiallyRelativeToAsOf() {
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");
        Instant eventTime = asOf.minus(Duration.ofDays(7));

        double decay = calculator.calculateEdgeDecay(eventTime, asOf, config.halfLife());
        assertThat(decay).isCloseTo(0.50, within(0.0001));
    }

    @Test
    @DisplayName("REQ-PROP-001 & REQ-PROP-003: Should calculate 1-hop path influence correctly")
    void shouldCalculateSingleHopInfluence() {
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");
        Instant eventTime = asOf.minus(Duration.ofDays(7)); // 1 half-life -> 0.5 decay
        double sourceRisk = 1.0;

        double influence = calculator.calculatePathInfluence(
            sourceRisk,
            List.of(RelationshipType.SHARED_DEVICE), // weight 0.90
            List.of(eventTime),
            asOf,
            config
        );

        // 1.0 * (0.90 * 0.50) = 0.45
        assertThat(influence).isCloseTo(0.45, within(0.001));
    }

    @Test
    @DisplayName("REQ-PROP-003: Should compute multi-hop product A -> B -> C correctly")
    void shouldComputeMultiHopProductWithBounds() {
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");
        Instant event1 = asOf.minus(Duration.ofDays(7)); // A -> B (SHARED_DEVICE, 7d ago) -> 0.90 * 0.50 = 0.45
        Instant event2 = asOf.minus(Duration.ofDays(7)); // B -> C (TRANSFERRED_TO, 7d ago) -> 0.60 * 0.50 = 0.30
        double sourceRisk = 1.0;

        double influence = calculator.calculatePathInfluence(
            sourceRisk,
            List.of(RelationshipType.SHARED_DEVICE, RelationshipType.TRANSFERRED_TO),
            List.of(event1, event2),
            asOf,
            config
        );

        // 1.0 * 0.45 * 0.30 = 0.135
        assertThat(influence).isCloseTo(0.135, within(0.001));
    }

    @Test
    @DisplayName("I-PROP-001 & I-PROP-002: Invariants - monotonic decay and strictly bounded in [0.0, 1.0]")
    void shouldMonotonicallyApproachZeroAsTimeIncreases() {
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");
        double sourceRisk = 0.90;

        double infl1d = calculator.calculatePathInfluence(
            sourceRisk,
            List.of(RelationshipType.SHARED_DEVICE),
            List.of(asOf.minus(Duration.ofDays(1))),
            asOf,
            config
        );

        double infl7d = calculator.calculatePathInfluence(
            sourceRisk,
            List.of(RelationshipType.SHARED_DEVICE),
            List.of(asOf.minus(Duration.ofDays(7))),
            asOf,
            config
        );

        double infl30d = calculator.calculatePathInfluence(
            sourceRisk,
            List.of(RelationshipType.SHARED_DEVICE),
            List.of(asOf.minus(Duration.ofDays(30))),
            asOf,
            config
        );

        assertThat(infl1d).isGreaterThan(infl7d);
        assertThat(infl7d).isGreaterThan(infl30d);
        assertThat(infl30d).isGreaterThanOrEqualTo(0.0);
        assertThat(infl1d).isLessThanOrEqualTo(1.0);
    }
}
