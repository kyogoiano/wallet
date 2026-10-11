package br.com.wallet.fraud.embeddings.internal.persistence;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * JDBC repository for querying fraud archetype centroids and calculating exact dot-product similarity.
 */
@Repository
public class PostgresArchetypeCentroidDao {

    private final JdbcTemplate jdbcTemplate;

    public PostgresArchetypeCentroidDao(@NonNull final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    public record ArchetypeCentroid(
        String archetypeId,
        String description,
        double[] centroidVector,
        double riskWeight
    ) {}

    public record ArchetypeSimilarity(
        String archetypeId,
        double riskWeight,
        double similarity
    ) {}

    @NonNull
    public List<ArchetypeCentroid> findAllArchetypes() {
        String sql = """
            SELECT archetype_id, description, centroid_vector::text AS vec_text, risk_weight
            FROM fraud_archetype_centroids
            """;

        List<ArchetypeCentroid> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String id = rs.getString("archetype_id");
            String desc = rs.getString("description");
            double[] vec = PostgresEntityFeaturesDao.parseVector(rs.getString("vec_text"));
            double weight = rs.getDouble("risk_weight");
            return new ArchetypeCentroid(id, desc, vec, weight);
        });

        if (list.isEmpty()) {
            seedDefaultsIfEmpty();
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String id = rs.getString("archetype_id");
                String desc = rs.getString("description");
                double[] vec = PostgresEntityFeaturesDao.parseVector(rs.getString("vec_text"));
                double weight = rs.getDouble("risk_weight");
                return new ArchetypeCentroid(id, desc, vec, weight);
            });
        }

        return list;
    }

    @NonNull
    public List<ArchetypeSimilarity> matchAgainstCentroids(@NonNull final double[] entityVector) {
        Objects.requireNonNull(entityVector, "entityVector cannot be null");

        String vectorStr = PostgresEntityFeaturesDao.formatVector(entityVector);

        // In pgvector, <#> is negative inner product; multiplying by -1 yields the exact dot product
        String sql = """
            SELECT archetype_id, risk_weight, (centroid_vector <#> CAST(? AS vector)) * -1.0 AS cosine_similarity
            FROM fraud_archetype_centroids
            ORDER BY cosine_similarity DESC
            """;

        List<ArchetypeSimilarity> matches = jdbcTemplate.query(sql, (rs, rowNum) -> new ArchetypeSimilarity(
            rs.getString("archetype_id"),
            rs.getDouble("risk_weight"),
            rs.getDouble("cosine_similarity")
        ), vectorStr);

        if (matches.isEmpty()) {
            seedDefaultsIfEmpty();
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ArchetypeSimilarity(
                rs.getString("archetype_id"),
                rs.getDouble("risk_weight"),
                rs.getDouble("cosine_similarity")
            ), vectorStr);
        }

        return matches;
    }

    public void seedDefaultsIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM fraud_archetype_centroids";
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
        if (count != null && count >= 3) {
            return;
        }

        String sql = """
            INSERT INTO fraud_archetype_centroids (archetype_id, description, centroid_vector, risk_weight)
            VALUES
                ('MONEY_MULE_RAPID_DRAIN', 'Rapid pass-through funds drain with high velocity and nocturnal activity', 
                 CAST(? AS vector), 0.95),
                ('SMURFING', 'High frequency micro-transactions with dispersed counterparties and low amount variance',
                 CAST(? AS vector), 0.85),
                ('ACCOUNT_TAKEOVER', 'Sudden new hardware/device switch with failed authentications and out-of-pattern spikes',
                 CAST(? AS vector), 0.90)
            ON CONFLICT (archetype_id) DO NOTHING
            """;

        jdbcTemplate.update(
            sql,
            "[0.1849, 0.2311, 0.1387, 0.3698, 0.3236, 0.1849, 0.1849, 0.4160, 0.3698, 0.2774, 0.0462, 0.0924, 0.1387, 0.2311, 0.0924, 0.3236]",
            "[0.3996, 0.0666, 0.0222, 0.3108, 0.1332, 0.3996, 0.3774, 0.1332, 0.3774, 0.3774, 0.0444, 0.0444, 0.0888, 0.1776, 0.0444, 0.2664]",
            "[0.1826, 0.2922, 0.2922, 0.2191, 0.1826, 0.1096, 0.2191, 0.3104, 0.2556, 0.0730, 0.1461, 0.3287, 0.3470, 0.3287, 0.1826, 0.3104]"
        );
    }
}
