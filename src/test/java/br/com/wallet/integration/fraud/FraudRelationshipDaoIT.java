package br.com.wallet.integration.fraud;

import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudRelationship;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipEvent;
import br.com.wallet.fraud.intelligence.domain.RelationshipType;
import br.com.wallet.fraud.intelligence.internal.persistence.PostgresFraudRelationshipDao;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Fraud Relationship DAO Integration Tests (PostgreSQL Schema & Query Verification)")
public class FraudRelationshipDaoIT extends DockerProperties {

    @Autowired
    private PostgresFraudRelationshipDao dao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-FRAUD-002: Should persist and retrieve FraudEntity with multi-signal risk decomposition")
    void shouldPersistAndRetrieveEntity() {
        UUID entityId = UUID.randomUUID();
        Instant now = Instant.now();

        FraudEntity entity = new FraudEntity(
            entityId,
            EntityType.USER,
            0.15,
            0.45,
            0.20,
            0.10,
            0.55,
            now,
            now,
            null
        );

        dao.upsertEntity(entity);

        Optional<FraudEntity> found = dao.findEntityById(entityId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(entityId);
        assertThat(found.get().entityType()).isEqualTo(EntityType.USER);
        assertThat(found.get().directRisk()).isEqualTo(0.15);
        assertThat(found.get().graphRisk()).isEqualTo(0.45);
        assertThat(found.get().behavioralRisk()).isEqualTo(0.20);
        assertThat(found.get().propagatedRisk()).isEqualTo(0.10);
        assertThat(found.get().finalRisk()).isEqualTo(0.55);

        // Update graph risk
        dao.updateGraphRisk(entityId, 0.88, now.plusSeconds(60));
        Optional<FraudEntity> updated = dao.findEntityById(entityId);
        assertThat(updated).isPresent();
        assertThat(updated.get().graphRisk()).isEqualTo(0.88);
        assertThat(updated.get().directRisk()).isEqualTo(0.15); // direct risk unmodified
    }

    @Test
    @DisplayName("REQ-FRAUD-002 & I-FRAUD-005: Should upsert relationships idempotently accumulating tx_count and amount")
    void shouldUpsertRelationshipsIdempotently() {
        UUID walletA = UUID.randomUUID();
        UUID walletB = UUID.randomUUID();
        Instant t1 = Instant.now().minusSeconds(300);
        Instant t2 = Instant.now();

        FraudRelationship rel1 = new FraudRelationship(
            walletA, walletB, RelationshipType.TRANSFERRED_TO, t1, t1, 1, new BigDecimal("150.00"), null
        );
        dao.upsertRelationship(rel1);

        Optional<FraudRelationship> found1 = dao.findRelationship(walletA, walletB, RelationshipType.TRANSFERRED_TO);
        assertThat(found1).isPresent();
        assertThat(found1.get().txCount()).isEqualTo(1);
        assertThat(found1.get().totalAmount()).isEqualByComparingTo(new BigDecimal("150.00"));

        // Second transfer
        FraudRelationship rel2 = new FraudRelationship(
            walletA, walletB, RelationshipType.TRANSFERRED_TO, t1, t2, 1, new BigDecimal("250.00"), null
        );
        dao.upsertRelationship(rel2);

        Optional<FraudRelationship> found2 = dao.findRelationship(walletA, walletB, RelationshipType.TRANSFERRED_TO);
        assertThat(found2).isPresent();
        assertThat(found2.get().txCount()).isEqualTo(2);
        assertThat(found2.get().totalAmount()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    @DisplayName("REQ-FRAUD-003 & I-FRAUD-003: Should record events and detect circular money loops (A -> B -> C -> A) within temporal window")
    void shouldRecordRelationshipEventsAndDetectCycles() {
        UUID walletA = UUID.randomUUID();
        UUID walletB = UUID.randomUUID();
        UUID walletC = UUID.randomUUID();
        Instant now = Instant.now();

        // A -> B at t - 20min
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletA, walletB, RelationshipType.TRANSFERRED_TO, now.minusSeconds(1200), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));
        // B -> C at t - 10min
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletB, walletC, RelationshipType.TRANSFERRED_TO, now.minusSeconds(600), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));
        // C -> A at t - 2min
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletC, walletA, RelationshipType.TRANSFERRED_TO, now.minusSeconds(120), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));

        // Evaluate cycles as of now within 1 hour window
        List<UUID> cycle = dao.detectCycles(walletA, Duration.ofHours(1), 4, now);
        assertThat(cycle).isNotEmpty();
        assertThat(cycle).containsExactly(walletA, walletB, walletC, walletA);
    }

    @Test
    @DisplayName("I-FRAUD-003: Should respect temporal cutoff (G_<=t) preventing future leakage in cycle detection")
    void shouldRespectTemporalCutoffWithoutFutureLeakage() {
        UUID walletA = UUID.randomUUID();
        UUID walletB = UUID.randomUUID();
        UUID walletC = UUID.randomUUID();
        Instant t0 = Instant.now().minusSeconds(3600);

        // A -> B at t0 - 10min
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletA, walletB, RelationshipType.TRANSFERRED_TO, t0.minusSeconds(600), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));
        // B -> C at t0 - 5min
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletB, walletC, RelationshipType.TRANSFERRED_TO, t0.minusSeconds(300), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));
        // C -> A at t0 + 15min (Future relative to t0!)
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletC, walletA, RelationshipType.TRANSFERRED_TO, t0.plusSeconds(900), UUID.randomUUID(), new BigDecimal("100.00"), null
        ));

        // Query as of t0 (The closing edge C -> A happened in the future relative to t0)
        List<UUID> cycleAtT0 = dao.detectCycles(walletA, Duration.ofHours(1), 4, t0);
        assertThat(cycleAtT0).isEmpty();

        // Query as of t0 + 20min (Now the cycle is complete)
        List<UUID> cycleLater = dao.detectCycles(walletA, Duration.ofHours(1), 4, t0.plusSeconds(1200));
        assertThat(cycleLater).containsExactly(walletA, walletB, walletC, walletA);
    }

    @Test
    @DisplayName("REQ-FRAUD-004: Should find shared entities (devices/IPs) across different users")
    void shouldFindSharedEntities() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();
        UUID sharedDevice = UUID.randomUUID();
        Instant now = Instant.now();

        // User1 and User2 share the same device
        dao.upsertRelationship(new FraudRelationship(user1, sharedDevice, RelationshipType.USES, now, now, 5, BigDecimal.ZERO, null));
        dao.upsertRelationship(new FraudRelationship(user2, sharedDevice, RelationshipType.USES, now, now, 3, BigDecimal.ZERO, null));
        // User3 uses another device
        dao.upsertRelationship(new FraudRelationship(user3, UUID.randomUUID(), RelationshipType.USES, now, now, 1, BigDecimal.ZERO, null));

        List<UUID> sharedWithUser1 = dao.findSharedEntities(user1, EntityType.DEVICE, 1, now);
        assertThat(sharedWithUser1).containsExactly(user2);
    }

    @Test
    @DisplayName("REQ-FRAUD-005: Should count unique counterparties in sliding window")
    void shouldCountUniqueCounterparties() {
        UUID walletSource = UUID.randomUUID();
        UUID walletDest1 = UUID.randomUUID();
        UUID walletDest2 = UUID.randomUUID();
        Instant now = Instant.now();

        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletSource, walletDest1, RelationshipType.TRANSFERRED_TO, now.minusSeconds(100), UUID.randomUUID(), BigDecimal.TEN, null
        ));
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletSource, walletDest1, RelationshipType.TRANSFERRED_TO, now.minusSeconds(50), UUID.randomUUID(), BigDecimal.TEN, null
        ));
        dao.recordRelationshipEvent(new FraudRelationshipEvent(
            UUID.randomUUID(), walletSource, walletDest2, RelationshipType.TRANSFERRED_TO, now.minusSeconds(10), UUID.randomUUID(), BigDecimal.TEN, null
        ));

        long count = dao.countUniqueCounterparties(walletSource, Duration.ofMinutes(10), now);
        assertThat(count).isEqualTo(2L);
    }
}
