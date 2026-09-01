package br.com.wallet.unit.fraud.intelligence;

import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudGraphQuery;
import br.com.wallet.fraud.intelligence.domain.GraphRiskSignals;
import br.com.wallet.fraud.intelligence.internal.engine.GraphPatternEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("GraphPatternEngine Unit Tests (Pattern Evaluation & Feature Provider)")
class GraphPatternEngineTest {

    private FraudGraphQuery graphQuery;
    private GraphPatternEngine engine;

    @BeforeEach
    void setup() {
        graphQuery = Mockito.mock(FraudGraphQuery.class);
        engine = new GraphPatternEngine(graphQuery);
    }

    @Test
    @DisplayName("Should detect circular loop and set cycleRisk to 1.0")
    void shouldDetectCycle() {
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();

        when(graphQuery.detectCycles(eq(walletId), any(Duration.class), anyInt(), eq(now)))
            .thenReturn(List.of(walletId, UUID.randomUUID(), UUID.randomUUID(), walletId));
        when(graphQuery.findSharedEntities(eq(walletId), eq(EntityType.DEVICE), anyInt(), eq(now)))
            .thenReturn(Collections.emptyList());
        when(graphQuery.countUniqueCounterparties(eq(walletId), any(Duration.class), eq(now)))
            .thenReturn(1L);

        GraphRiskSignals signals = engine.evaluateGraphSignals(walletId, now);
        assertThat(signals.cycleRisk()).isEqualTo(1.0);
        assertThat(signals.sharedIdentityRisk()).isEqualTo(0.0);
        assertThat(signals.fanOutRisk()).isEqualTo(0.0);
        assertThat(signals.calculateCompositeScore()).isGreaterThan(0.35);
    }

    @Test
    @DisplayName("Should evaluate shared identity risk based on shared entity count")
    void shouldEvaluateSharedIdentityRisk() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        when(graphQuery.detectCycles(eq(userId), any(Duration.class), anyInt(), eq(now)))
            .thenReturn(Collections.emptyList());
        when(graphQuery.findSharedEntities(eq(userId), eq(EntityType.DEVICE), anyInt(), eq(now)))
            .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID())); // 2 shared users
        when(graphQuery.countUniqueCounterparties(eq(userId), any(Duration.class), eq(now)))
            .thenReturn(2L);

        GraphRiskSignals signals = engine.evaluateGraphSignals(userId, now);
        assertThat(signals.cycleRisk()).isEqualTo(0.0);
        assertThat(signals.sharedIdentityRisk()).isEqualTo(0.70);
    }
}
