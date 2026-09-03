package br.com.wallet.unit.fraud.investigation;

import br.com.wallet.fraud.embeddings.api.ArchetypeMatchingService;
import br.com.wallet.fraud.embeddings.api.model.ArchetypeMatch;
import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.domain.FraudRelationshipStore;
import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.internal.evidence.InvestigationContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvestigationContextBuilder Unit Tests (Assembling Atomic Evidence Items)")
class InvestigationContextBuilderTest {

    @Mock
    private FraudRelationshipStore relationshipStore;

    @Mock
    private BehavioralFeatureStore featureStore;

    @Mock
    private ArchetypeMatchingService archetypeMatcher;

    private InvestigationContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new InvestigationContextBuilder(relationshipStore, featureStore, archetypeMatcher);
    }

    @Test
    @DisplayName("REQ-VEC-006: Should assemble atomic evidence items from multi-tier facts")
    void shouldAssembleAtomicEvidenceItems() {
        UUID entityId = UUID.randomUUID();

        FraudEntity entity = new FraudEntity(
            entityId, EntityType.USER, 0.4, 0.75, 0.0, 0.6, 0.0, Instant.now(), Instant.now(), null
        );

        BehavioralFeatureVector vector = new BehavioralFeatureVector(
            entityId, new double[16], 1.8, 15L, new BigDecimal("4500.00")
        );

        ArchetypeMatch match = new ArchetypeMatch("MONEY_MULE_RAPID_DRAIN", 0.88, 0.95, 1.8, 0.836);

        when(relationshipStore.findEntityById(entityId)).thenReturn(Optional.of(entity));
        when(featureStore.findFeatures(entityId)).thenReturn(Optional.of(vector));
        when(archetypeMatcher.findTopArchetypeMatch(any())).thenReturn(match);

        InvestigationEvidence evidence = builder.buildEvidence(entityId);

        assertThat(evidence.risks().directRisk()).isEqualTo(0.4);
        assertThat(evidence.risks().graphRisk()).isEqualTo(0.75);
        assertThat(evidence.risks().propagatedRisk()).isEqualTo(0.6);
        assertThat(evidence.risks().topArchetype()).isEqualTo("MONEY_MULE_RAPID_DRAIN");
        assertThat(evidence.risks().archetypeSimilarity()).isEqualTo(0.88);

        assertThat(evidence.evidenceItems()).extracting(item -> item.id())
            .contains("ARCHETYPE-001", "GRAPH-001", "TEMPORAL-001");
    }
}
