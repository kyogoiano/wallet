package br.com.wallet.unit.fraud.intelligence;

import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudFeatureProvider;
import br.com.wallet.fraud.intelligence.domain.FraudRelationship;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipEvent;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.intelligence.domain.GraphRiskSignals;
import br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer;
import br.com.wallet.fraud.intelligence.projector.RelationalGraphProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RelationalGraphProjector Unit Tests (Event Ingestion & Score Feedback)")
class RelationalGraphProjectorTest {

    private FraudRelationshipStore store;
    private FraudFeatureProvider featureProvider;
    private HotRiskMaterializer materializer;
    private RelationalGraphProjector projector;

    @BeforeEach
    void setup() {
        store = Mockito.mock(FraudRelationshipStore.class);
        featureProvider = Mockito.mock(FraudFeatureProvider.class);
        materializer = Mockito.mock(HotRiskMaterializer.class);
        projector = new RelationalGraphProjector(store, featureProvider, materializer);

        when(materializer.materializeGraphRisk(any(UUID.class), anyDouble()))
            .thenReturn(CompletableFuture.completedFuture("OK"));
    }

    @Test
    @DisplayName("REQ-FRAUD-001: Should project transfer into entities, event, aggregate edge and hot cache")
    void shouldProjectTransferCorrectly() {
        UUID walletA = UUID.randomUUID();
        UUID walletB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        Instant now = Instant.now();

        when(featureProvider.evaluateGraphSignals(eq(walletA), eq(now)))
            .thenReturn(new GraphRiskSignals(0.0, 0.0, 0.0, 0.0, 0.0));

        projector.projectTransfer(walletA, walletB, userA, userB, new BigDecimal("500.00"), opId, now);

        // Verify entities created
        verify(store, times(4)).upsertEntity(any(FraudEntity.class));

        // Verify immutable event recorded
        ArgumentCaptor<FraudRelationshipEvent> eventCaptor = ArgumentCaptor.forClass(FraudRelationshipEvent.class);
        verify(store).recordRelationshipEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sourceId()).isEqualTo(walletA);
        assertThat(eventCaptor.getValue().targetId()).isEqualTo(walletB);
        assertThat(eventCaptor.getValue().operationId()).isEqualTo(opId);

        // Verify relationships upserted (walletA -> walletB, userA -> walletA, userB -> walletB)
        verify(store, times(3)).upsertRelationship(any(FraudRelationship.class));

        // Verify hot risk materialized
        verify(materializer).materializeGraphRisk(eq(userA), anyDouble());
        verify(materializer).materializeGraphRisk(eq(walletA), anyDouble());
    }
}
