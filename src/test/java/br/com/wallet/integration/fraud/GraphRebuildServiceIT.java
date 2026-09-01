package br.com.wallet.integration.fraud;

import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudRelationship;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipEvent;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.internal.materializer.HotRiskMaterializer;
import br.com.wallet.fraud.intelligence.internal.service.GraphRebuildService;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("GraphRebuildService Integration Tests (Historical Event Replay & Hot State Restoration)")
public class GraphRebuildServiceIT extends DockerProperties {

    @Autowired
    private GraphRebuildService rebuildService;

    @Autowired
    private FraudRelationshipStore store;

    @Autowired
    private HotRiskMaterializer materializer;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-FRAUD-007 & I-FRAUD-007: Should rebuild aggregate relationships and rematerialize Dragonfly hot state from events")
    void shouldRebuildGraphAndHotStateFromEvents() throws Exception {
        UUID walletA = UUID.randomUUID();
        UUID walletB = UUID.randomUUID();
        UUID walletC = UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Setup entities
        store.upsertEntity(FraudEntity.create(walletA, EntityType.WALLET, now));
        store.upsertEntity(FraudEntity.create(walletB, EntityType.WALLET, now));
        store.upsertEntity(FraudEntity.create(walletC, EntityType.WALLET, now));

        // 2. Insert temporal cycle events: A -> B -> C -> A
        store.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletA, walletB, RelationshipType.TRANSFERRED_TO, now.minusSeconds(1800), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));
        store.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletB, walletC, RelationshipType.TRANSFERRED_TO, now.minusSeconds(900), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));
        store.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletC, walletA, RelationshipType.TRANSFERRED_TO, now.minusSeconds(300), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));

        // 3. Trigger rebuild
        GraphRebuildService.RebuildSummary summary = rebuildService.rebuildAll(now);
        assertThat(summary.totalEventsProcessed()).isEqualTo(3);
        assertThat(summary.entitiesRecomputed()).isGreaterThanOrEqualTo(3);

        // 4. Verify reconstructed aggregate edges
        Optional<FraudRelationship> ab = store.findRelationship(walletA, walletB, RelationshipType.TRANSFERRED_TO);
        assertThat(ab).isPresent();
        assertThat(ab.get().txCount()).isEqualTo(1);
        assertThat(ab.get().totalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        // 5. Verify Dragonfly hot cache is rematerialized with cycle risk
        Double hotRiskA = materializer.getHotGraphRisk(walletA).toCompletableFuture().get();
        assertThat(hotRiskA).isGreaterThan(0.35); // Cycle detected!
    }
}
