package br.com.wallet.integration.fraud.embeddings;

import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.persistence.PostgresEntityFeaturesDao;
import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("PostgresEntityFeaturesDao Integration Tests (pgvector vector(16), Magnitude & Volume)")
public class PostgresEntityFeaturesDaoIT extends DockerProperties {

    @Autowired
    private PostgresEntityFeaturesDao dao;

    @Autowired
    private PostgresFraudRelationshipDao relationshipDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-VEC-001 & REQ-VEC-008: Should persist and retrieve BehavioralFeatureVector with pgvector vector(16)")
    void shouldPersistAndRetrieveVector16() {
        UUID entityId = UUID.randomUUID();
        // Create prerequisite fraud_entity row
        relationshipDao.upsertEntity(new FraudEntity(
            entityId, EntityType.USER, 0.0, 0.0, 0.0, 0.0, 0.0, Instant.now(), Instant.now(), null
        ));

        double[] sampleVec = new double[16];
        for (int i = 0; i < 16; i++) {
            sampleVec[i] = 0.25;
        }

        BehavioralFeatureVector vector = new BehavioralFeatureVector(
            entityId,
            sampleVec,
            1.85,
            42L,
            new BigDecimal("12500.5000")
        );

        dao.upsertFeatures(vector);

        Optional<BehavioralFeatureVector> retrieved = dao.findFeatures(entityId);
        assertThat(retrieved).isPresent();

        BehavioralFeatureVector retrievedVec = retrieved.get();
        assertThat(retrievedVec.entityId()).isEqualTo(entityId);
        assertThat(retrievedVec.featureMagnitude()).isCloseTo(1.85, within(0.0001));
        assertThat(retrievedVec.transactionCount()).isEqualTo(42L);
        assertThat(retrievedVec.transactionVolume()).isEqualByComparingTo("12500.5000");

        for (int i = 0; i < 16; i++) {
            assertThat(retrievedVec.vector()[i]).isCloseTo(0.25, within(0.0001));
        }
    }

    @Test
    @DisplayName("REQ-VEC-003: Should update behavioral_risk in fraud_entities preserving dimension isolation")
    void shouldUpdateBehavioralRisk() {
        UUID entityId = UUID.randomUUID();
        relationshipDao.upsertEntity(new FraudEntity(
            entityId, EntityType.USER, 0.5, 0.3, 0.0, 0.4, 0.0, Instant.now(), Instant.now(), null
        ));

        dao.updateBehavioralRisk(entityId, 0.88);

        Optional<FraudEntity> updated = relationshipDao.findEntityById(entityId);
        assertThat(updated).isPresent();
        assertThat(updated.get().behavioralRisk()).isCloseTo(0.88, within(0.0001));
        // Direct, graph, and propagated risk must remain untouched
        assertThat(updated.get().directRisk()).isCloseTo(0.5, within(0.0001));
        assertThat(updated.get().graphRisk()).isCloseTo(0.3, within(0.0001));
        assertThat(updated.get().propagatedRisk()).isCloseTo(0.4, within(0.0001));
    }
}
