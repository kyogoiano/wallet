package br.com.wallet.intelligence.internal.persistence;

import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SubscriptionDao {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Subscription> rowMapper = new SubscriptionRowMapper();

    public SubscriptionDao(@NonNull final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate cannot be null");
    }

    /**
     * Attempts to atomically record an eventId for durable idempotency (I-SUB-008).
     * Returns true if event was successfully recorded (not previously processed), false if duplicate.
     */
    public boolean tryRecordProcessedEvent(@NonNull final UUID eventId, @NonNull final String tenantId) {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        int rows = jdbcTemplate.update(
                "INSERT INTO intelligence_processed_events (event_id, tenant_id, processed_at) VALUES (?, ?, NOW()) " +
                        "ON CONFLICT (event_id) DO NOTHING",
                eventId, tenantId
        );
        return rows > 0;
    }

    public Optional<Subscription> findBySeries(
            @NonNull final String tenantId,
            @NonNull final UUID walletId,
            @NonNull final UUID counterpartyId
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(counterpartyId, "counterpartyId cannot be null");
        List<Subscription> results = jdbcTemplate.query(
                "SELECT * FROM subscriptions WHERE tenant_id = ? AND wallet_id = ? AND counterparty_id = ?",
                rowMapper,
                tenantId, walletId, counterpartyId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Subscription> findByWalletId(
            @NonNull final String tenantId,
            @NonNull final UUID walletId,
            @Nullable final SubscriptionStatus status
    ) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        if (status != null) {
            return jdbcTemplate.query(
                    "SELECT * FROM subscriptions WHERE tenant_id = ? AND wallet_id = ? AND status = ? ORDER BY created_at DESC",
                    rowMapper,
                    tenantId, walletId, status.name()
            );
        }
        return jdbcTemplate.query(
                "SELECT * FROM subscriptions WHERE tenant_id = ? AND wallet_id = ? ORDER BY created_at DESC",
                rowMapper,
                tenantId, walletId
        );
    }

    public void upsert(@NonNull final Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");
        jdbcTemplate.update(
                "INSERT INTO subscriptions (" +
                        "id, tenant_id, wallet_id, counterparty_id, cadence, status, price_state, " +
                        "classification, average_amount, last_amount, confidence, observed_cycles, " +
                        "last_observed_at, next_expected_at, variance_type, created_at, updated_at" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (tenant_id, wallet_id, counterparty_id) DO UPDATE SET " +
                        "cadence = EXCLUDED.cadence, " +
                        "status = EXCLUDED.status, " +
                        "price_state = EXCLUDED.price_state, " +
                        "classification = EXCLUDED.classification, " +
                        "average_amount = EXCLUDED.average_amount, " +
                        "last_amount = EXCLUDED.last_amount, " +
                        "confidence = EXCLUDED.confidence, " +
                        "observed_cycles = EXCLUDED.observed_cycles, " +
                        "last_observed_at = EXCLUDED.last_observed_at, " +
                        "next_expected_at = EXCLUDED.next_expected_at, " +
                        "variance_type = EXCLUDED.variance_type, " +
                        "updated_at = EXCLUDED.updated_at",
                subscription.id(),
                subscription.tenantId(),
                subscription.walletId(),
                subscription.counterpartyId(),
                subscription.cadence().name(),
                subscription.status().name(),
                subscription.priceState().name(),
                subscription.classification(),
                subscription.averageAmount(),
                subscription.lastAmount(),
                subscription.confidence(),
                subscription.observedCycles(),
                Timestamp.from(subscription.lastObservedAt()),
                subscription.nextExpectedAt() != null ? Timestamp.from(subscription.nextExpectedAt()) : null,
                subscription.varianceType().name(),
                Timestamp.from(subscription.createdAt()),
                Timestamp.from(subscription.updatedAt())
        );
    }

    private static class SubscriptionRowMapper implements RowMapper<Subscription> {
        @Override
        public Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp nextExpectedTimestamp = rs.getTimestamp("next_expected_at");
            return new Subscription(
                    rs.getObject("id", UUID.class),
                    rs.getString("tenant_id"),
                    rs.getObject("wallet_id", UUID.class),
                    rs.getObject("counterparty_id", UUID.class),
                    Cadence.valueOf(rs.getString("cadence")),
                    SubscriptionStatus.valueOf(rs.getString("status")),
                    PriceState.valueOf(rs.getString("price_state")),
                    rs.getString("classification"),
                    rs.getBigDecimal("average_amount"),
                    rs.getBigDecimal("last_amount"),
                    rs.getBigDecimal("confidence"),
                    rs.getInt("observed_cycles"),
                    VarianceType.valueOf(rs.getString("variance_type")),
                    nextExpectedTimestamp != null ? nextExpectedTimestamp.toInstant() : null,
                    rs.getTimestamp("last_observed_at").toInstant(),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
            );
        }
    }
}
