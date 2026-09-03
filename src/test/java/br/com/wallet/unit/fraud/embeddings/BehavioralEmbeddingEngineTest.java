package br.com.wallet.unit.fraud.embeddings;

import br.com.wallet.fraud.embeddings.api.ArchetypeMatchingService;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.extraction.EntityTransactionalMetrics;
import br.com.wallet.fraud.embeddings.internal.extraction.FeatureVectorExtractor;
import br.com.wallet.fraud.embeddings.internal.normalization.FeatureNormalizer;
import br.com.wallet.fraud.embeddings.internal.orchestration.DefaultBehavioralEmbeddingEngine;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BehavioralEmbeddingEngine Unit Tests (Extraction, Normalization & Risk Persistence)")
class BehavioralEmbeddingEngineTest {

    @Mock
    private BehavioralFeatureStore featureStore;

    @Mock
    private ArchetypeMatchingService matchingService;

    private DefaultBehavioralEmbeddingEngine engine;

    @BeforeEach
    void setUp() {
        FeatureVectorExtractor extractor = new FeatureVectorExtractor();
        FeatureNormalizer normalizer = new FeatureNormalizer();
        engine = new DefaultBehavioralEmbeddingEngine(extractor, normalizer, featureStore, matchingService);
    }

    @Test
    @DisplayName("REQ-VEC-002: Should extract, normalize and persist behavioral feature vector")
    void shouldExtractAndPersistVector() {
        UUID entityId = UUID.randomUUID();
        EntityTransactionalMetrics metrics = new EntityTransactionalMetrics(
            100, 5000.0, 1000.0, 5, 20, 10, 5, 2, 8, 10, 8, 10, 0, 0, 1, 5, 0, 15000.0
        );

        BehavioralFeatureVector vector = engine.extractAndPersist(entityId, metrics);

        assertThat(vector.entityId()).isEqualTo(entityId);
        assertThat(vector.transactionCount()).isEqualTo(100);
        assertThat(vector.transactionVolume()).isEqualByComparingTo("15000.0");
        assertThat(vector.featureMagnitude()).isGreaterThan(0.0);

        verify(featureStore).upsertFeatures(vector);
    }

    @Test
    @DisplayName("REQ-VEC-003: Should evaluate top archetype match and update behavioral risk")
    void shouldEvaluateAndPersistRisk() {
        UUID entityId = UUID.randomUUID();
        BehavioralFeatureVector vector = new BehavioralFeatureVector(
            entityId, new double[16], 1.5, 10L, new BigDecimal("1000.00")
        );

        ArchetypeMatch match = new ArchetypeMatch("MONEY_MULE_RAPID_DRAIN", 0.85, 0.95, 1.5, 0.8075);

        when(featureStore.findFeatures(entityId)).thenReturn(Optional.of(vector));
        when(matchingService.findTopArchetypeMatch(vector)).thenReturn(match);

        ArchetypeMatch result = engine.evaluateAndPersistRisk(entityId);

        assertThat(result.archetypeId()).isEqualTo("MONEY_MULE_RAPID_DRAIN");
        assertThat(result.weightedSimilarity()).isEqualTo(0.8075);

        verify(featureStore).updateBehavioralRisk(eq(entityId), eq(0.8075));
    }
}
