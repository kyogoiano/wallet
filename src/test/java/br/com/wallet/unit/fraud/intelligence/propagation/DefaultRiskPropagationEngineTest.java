package br.com.wallet.unit.fraud.intelligence.propagation;

import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.internal.propagation.DefaultRiskPropagationEngine;
import br.com.wallet.fraud.intelligence.internal.propagation.MultiPathAggregator;
import br.com.wallet.fraud.intelligence.internal.propagation.PathInfluenceCalculator;
import br.com.wallet.fraud.intelligence.internal.propagation.PostgresRiskPropagationDao;
import br.com.wallet.fraud.intelligence.propagation.PropagationConfig;
import br.com.wallet.fraud.intelligence.propagation.PropagationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DefaultRiskPropagationEngine Unit Tests (End-to-End Propagation & Dimension Isolation)")
class DefaultRiskPropagationEngineTest {

    private PostgresRiskPropagationDao propagationDao;
    private PathInfluenceCalculator pathCalculator;
    private MultiPathAggregator pathAggregator;
    private PropagationConfig config;
    private DefaultRiskPropagationEngine engine;

    @BeforeEach
    void setUp() {
        propagationDao = Mockito.mock(PostgresRiskPropagationDao.class);
        pathCalculator = new PathInfluenceCalculator();
        pathAggregator = new MultiPathAggregator();
        config = PropagationConfig.defaultConfig();
        engine = new DefaultRiskPropagationEngine(propagationDao, pathCalculator, pathAggregator, config);
    }

    @Test
    @DisplayName("Should skip propagation if source entity direct risk is 0.0")
    void shouldSkipIfSourceRiskIsZero() {
        UUID sourceId = UUID.randomUUID();
        Instant asOf = Instant.now();

        when(propagationDao.getDirectRisk(sourceId)).thenReturn(0.0);

        PropagationResult result = engine.evaluateEntity(sourceId, asOf);

        assertThat(result.rootSourceId()).isEqualTo(sourceId);
        assertThat(result.pathsEvaluated()).isEqualTo(0);
        assertThat(result.propagatedRisks()).isEmpty();
        verify(propagationDao, never()).findPathsFromSource(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("REQ-PROP-003 & REQ-PROP-004: Should propagate risk along single-hop and multi-hop paths")
    void shouldPropagateRiskAlongPaths() {
        UUID sourceId = UUID.randomUUID();
        UUID targetB = UUID.randomUUID();
        UUID targetC = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        when(propagationDao.getDirectRisk(sourceId)).thenReturn(1.0);

        // Path 1: Source -> TargetB (SHARED_DEVICE, 7d ago -> infl 0.45)
        PostgresRiskPropagationDao.DiscoveredPath path1 = new PostgresRiskPropagationDao.DiscoveredPath(
            sourceId,
            targetB,
            1,
            List.of(sourceId, targetB),
            List.of(RelationshipType.SHARED_DEVICE),
            List.of(asOf.minus(Duration.ofDays(7)))
        );

        // Path 2: Source -> TargetB -> TargetC (SHARED_DEVICE 7d, TRANSFERRED_TO 7d -> infl 0.135)
        PostgresRiskPropagationDao.DiscoveredPath path2 = new PostgresRiskPropagationDao.DiscoveredPath(
            sourceId,
            targetC,
            2,
            List.of(sourceId, targetB, targetC),
            List.of(RelationshipType.SHARED_DEVICE, RelationshipType.TRANSFERRED_TO),
            List.of(asOf.minus(Duration.ofDays(7)), asOf.minus(Duration.ofDays(7)))
        );

        when(propagationDao.findPathsFromSource(eq(sourceId), eq(asOf), anyInt(), anyInt()))
            .thenReturn(List.of(path1, path2));

        PropagationResult result = engine.evaluateEntity(sourceId, asOf);

        assertThat(result.pathsEvaluated()).isEqualTo(2);
        assertThat(result.propagatedRisks()).hasSize(2);

        assertThat(result.propagatedRisks().get(targetB).propagatedRisk()).isCloseTo(0.45, within(0.001));
        assertThat(result.propagatedRisks().get(targetB).shortestHopCount()).isEqualTo(1);
        assertThat(result.propagatedRisks().get(targetB).primaryRelationship()).isEqualTo(RelationshipType.SHARED_DEVICE);

        assertThat(result.propagatedRisks().get(targetC).propagatedRisk()).isCloseTo(0.135, within(0.001));
        assertThat(result.propagatedRisks().get(targetC).shortestHopCount()).isEqualTo(2);

        verify(propagationDao).persistPropagatedRisks(result.propagatedRisks());
    }

    @Test
    @DisplayName("REQ-PROP-004: Should aggregate multiple converging paths reaching the same target")
    void shouldAggregateMultiPathConvergence() {
        UUID sourceId = UUID.randomUUID();
        UUID targetD = UUID.randomUUID();
        Instant asOf = Instant.parse("2026-09-01T12:00:00Z");

        when(propagationDao.getDirectRisk(sourceId)).thenReturn(1.0);

        // Path 1 (SHARED_DEVICE: weight 0.90, decay 0.50 -> infl = 0.45)
        PostgresRiskPropagationDao.DiscoveredPath path1 = new PostgresRiskPropagationDao.DiscoveredPath(
            sourceId, targetD, 1, List.of(sourceId, targetD),
            List.of(RelationshipType.SHARED_DEVICE), List.of(asOf.minus(Duration.ofDays(7)))
        );

        // Path 2 (TRANSFERRED_TO: weight 0.60, decay 0.50 -> infl = 0.30)
        PostgresRiskPropagationDao.DiscoveredPath path2 = new PostgresRiskPropagationDao.DiscoveredPath(
            sourceId, targetD, 1, List.of(sourceId, targetD),
            List.of(RelationshipType.TRANSFERRED_TO), List.of(asOf.minus(Duration.ofDays(7)))
        );

        when(propagationDao.findPathsFromSource(eq(sourceId), eq(asOf), anyInt(), anyInt()))
            .thenReturn(List.of(path1, path2));

        PropagationResult result = engine.evaluateEntity(sourceId, asOf);

        assertThat(result.propagatedRisks()).hasSize(1);
        // R = 1 - (1 - 0.45) * (1 - 0.30) = 1 - 0.55 * 0.70 = 1 - 0.385 = 0.615
        assertThat(result.propagatedRisks().get(targetD).propagatedRisk()).isCloseTo(0.615, within(0.001));
    }
}
