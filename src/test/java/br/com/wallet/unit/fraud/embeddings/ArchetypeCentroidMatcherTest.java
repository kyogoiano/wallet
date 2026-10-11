package br.com.wallet.unit.fraud.embeddings;

import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.internal.matching.DefaultArchetypeMatcher;
import br.com.wallet.fraud.embeddings.internal.persistence.PostgresArchetypeCentroidDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ArchetypeCentroidMatcher Unit Tests (Exact Centroid Matching, Intensity & Zero-Activity Neutrality)")
class ArchetypeCentroidMatcherTest {

    @Mock
    private PostgresArchetypeCentroidDao centroidDao;

    private DefaultArchetypeMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new DefaultArchetypeMatcher(centroidDao);
    }

    @Test
    @DisplayName("REQ-VEC-003: Should find top archetype match by weighted similarity and expose intensity")
    void shouldFindTopArchetypeMatchAndExposeIntensity() {
        UUID entityId = UUID.randomUUID();
        double[] unitVec = new double[16];
        unitVec[0] = 1.0; // dummy unit vector
        BehavioralFeatureVector vector = new BehavioralFeatureVector(
            entityId, unitVec, 2.50, 20L, new BigDecimal("5000.00")
        );

        when(centroidDao.matchAgainstCentroids(any())).thenReturn(List.of(
            new PostgresArchetypeCentroidDao.ArchetypeSimilarity("SMURFING", 0.85, 0.70), // weighted = 0.595
            new PostgresArchetypeCentroidDao.ArchetypeSimilarity("MONEY_MULE_RAPID_DRAIN", 0.95, 0.80) // weighted = 0.760
        ));

        ArchetypeMatch topMatch = matcher.findTopArchetypeMatch(vector);

        assertThat(topMatch.archetypeId()).isEqualTo("MONEY_MULE_RAPID_DRAIN");
        assertThat(topMatch.directionalSimilarity()).isCloseTo(0.80, within(0.0001));
        assertThat(topMatch.archetypeWeight()).isCloseTo(0.95, within(0.0001));
        assertThat(topMatch.behavioralIntensity()).isCloseTo(2.50, within(0.0001));
        assertThat(topMatch.weightedSimilarity()).isCloseTo(0.76, within(0.0001));

        double risk = matcher.calculateBehavioralRisk(vector);
        assertThat(risk).isCloseTo(0.76, within(0.0001));
    }

    @Test
    @DisplayName("I-VEC-008: Zero Activity Neutrality should return ArchetypeMatch.none and risk 0.0 without querying DB")
    void shouldReturnNoneAndZeroRiskWhenActivityIsZero() {
        UUID entityId = UUID.randomUUID();
        BehavioralFeatureVector inactiveVector = BehavioralFeatureVector.inactive(entityId);

        ArchetypeMatch match = matcher.findTopArchetypeMatch(inactiveVector);

        assertThat(match.archetypeId()).isEqualTo("NONE");
        assertThat(match.directionalSimilarity()).isEqualTo(0.0);
        assertThat(match.behavioralIntensity()).isEqualTo(0.0);
        assertThat(match.weightedSimilarity()).isEqualTo(0.0);

        double risk = matcher.calculateBehavioralRisk(inactiveVector);
        assertThat(risk).isEqualTo(0.0);

        // Verify zero database query overhead
        verify(centroidDao, never()).matchAgainstCentroids(any());
    }

    @Test
    @DisplayName("REQ-VEC-003: Should clamp negative dot-product similarities to 0.0")
    void shouldClampNegativeSimilaritiesToZero() {
        UUID entityId = UUID.randomUUID();
        double[] unitVec = new double[16];
        unitVec[0] = 1.0;
        BehavioralFeatureVector vector = new BehavioralFeatureVector(
            entityId, unitVec, 1.2, 5L, new BigDecimal("100.00")
        );

        when(centroidDao.matchAgainstCentroids(any())).thenReturn(List.of(
            new PostgresArchetypeCentroidDao.ArchetypeSimilarity("ACCOUNT_TAKEOVER", 0.90, -0.45)
        ));

        ArchetypeMatch match = matcher.findTopArchetypeMatch(vector);
        assertThat(match.directionalSimilarity()).isCloseTo(-0.45, within(0.0001));
        assertThat(match.weightedSimilarity()).isEqualTo(0.0);
    }
}
