package br.com.wallet.fraud.embeddings.internal.persistence;

import br.com.wallet.fraud.embeddings.api.model.BehavioralFeatureVector;
import br.com.wallet.fraud.embeddings.spi.BehavioralFeatureStore;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for storing and retrieving entity behavioral features using PostgreSQL pgvector.
 */
@Repository
public class PostgresEntityFeaturesDao implements BehavioralFeatureStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresEntityFeaturesDao.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresEntityFeaturesDao(@NonNull final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    @Override
    public void upsertFeatures(@NonNull final BehavioralFeatureVector vector) {
        Objects.requireNonNull(vector, "vector cannot be null");

        String vectorStr = formatVector(vector.vector());

        String sql = """
            INSERT INTO fraud_entity_features (
                entity_id, feature_version, behavioral_vector, feature_magnitude, 
                transaction_count, transaction_volume, updated_at
            ) VALUES (?, 1, ?::vector, ?, ?, ?, NOW())
            ON CONFLICT (entity_id) DO UPDATE SET
                feature_version = EXCLUDED.feature_version,
                behavioral_vector = EXCLUDED.behavioral_vector,
                feature_magnitude = EXCLUDED.feature_magnitude,
                transaction_count = EXCLUDED.transaction_count,
                transaction_volume = EXCLUDED.transaction_volume,
                updated_at = NOW()
            """;

        jdbcTemplate.update(
            sql,
            vector.entityId(),
            vectorStr,
            vector.featureMagnitude(),
            vector.transactionCount(),
            vector.transactionVolume()
        );
        log.debug("Upserted behavioral vector for entity: {}", vector.entityId());
    }

    @Override
    @NonNull
    public Optional<BehavioralFeatureVector> findFeatures(@NonNull final UUID entityId) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        String sql = """
            SELECT entity_id, behavioral_vector::text AS vec_text, feature_magnitude, 
                   transaction_count, transaction_volume
            FROM fraud_entity_features
            WHERE entity_id = ?
            """;

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                UUID id = rs.getObject("entity_id", UUID.class);
                String vecText = rs.getString("vec_text");
                double magnitude = rs.getDouble("feature_magnitude");
                long count = rs.getLong("transaction_count");
                BigDecimal volume = rs.getBigDecimal("transaction_volume");

                double[] vec = parseVector(vecText);
                return new BehavioralFeatureVector(id, vec, magnitude, count, volume != null ? volume : BigDecimal.ZERO);
            }, entityId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void updateBehavioralRisk(@NonNull final UUID entityId, final double behavioralRisk) {
        Objects.requireNonNull(entityId, "entityId cannot be null");

        String sql = """
            UPDATE fraud_entities
            SET behavioral_risk = ?, updated_at = NOW()
            WHERE id = ?
            """;

        jdbcTemplate.update(sql, behavioralRisk, entityId);
        log.debug("Updated behavioral_risk={} for entity: {}", behavioralRisk, entityId);
    }

    public static String formatVector(double[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            sb.append(vec[i]);
            if (i < vec.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static double[] parseVector(String vecText) {
        if (vecText == null || vecText.isBlank()) {
            return new double[16];
        }
        String clean = vecText.replace("[", "").replace("]", "").trim();
        if (clean.isEmpty()) {
            return new double[16];
        }
        String[] parts = clean.split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }
}
