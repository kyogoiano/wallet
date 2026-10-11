package br.com.wallet.integration.fraud.embeddings;

import br.com.wallet.fraud.embeddings.internal.persistence.PostgresArchetypeCentroidDao;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("PostgresArchetypeCentroidDao Integration Tests (Seeded Archetypes & Dot-Product Similarity)")
public class PostgresArchetypeCentroidDaoIT extends DockerProperties {

    @Autowired
    private PostgresArchetypeCentroidDao dao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
        dao.seedDefaultsIfEmpty();
    }

    @Test
    @DisplayName("REQ-VEC-001: Should retrieve seeded fraud archetype centroids from PostgreSQL")
    void shouldFindAllArchetypes() {
        List<PostgresArchetypeCentroidDao.ArchetypeCentroid> archetypes = dao.findAllArchetypes();

        assertThat(archetypes).hasSizeGreaterThanOrEqualTo(3);
        assertThat(archetypes).extracting(PostgresArchetypeCentroidDao.ArchetypeCentroid::archetypeId)
            .contains("MONEY_MULE_RAPID_DRAIN", "SMURFING", "ACCOUNT_TAKEOVER");
    }

    @Test
    @DisplayName("REQ-VEC-003: Should compute exact dot-product similarity against centroids in SQL")
    void shouldMatchAgainstCentroidsUsingDotProduct() {
        List<PostgresArchetypeCentroidDao.ArchetypeCentroid> archetypes = dao.findAllArchetypes();
        PostgresArchetypeCentroidDao.ArchetypeCentroid mule = archetypes.stream()
            .filter(a -> "MONEY_MULE_RAPID_DRAIN".equals(a.archetypeId()))
            .findFirst()
            .orElseThrow();

        // Querying with the exact same vector as the Money Mule centroid
        List<PostgresArchetypeCentroidDao.ArchetypeSimilarity> matches = dao.matchAgainstCentroids(mule.centroidVector());

        assertThat(matches).isNotEmpty();
        PostgresArchetypeCentroidDao.ArchetypeSimilarity topMatch = matches.getFirst();

        assertThat(topMatch.archetypeId()).isEqualTo("MONEY_MULE_RAPID_DRAIN");
        assertThat(topMatch.similarity()).isCloseTo(1.0, within(0.001));
    }
}
