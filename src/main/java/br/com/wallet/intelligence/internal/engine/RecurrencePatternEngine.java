package br.com.wallet.intelligence.internal.engine;

import br.com.wallet.intelligence.api.event.SubscriptionPriceSpikeEvent;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.intelligence.internal.domain.Subscription;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic Mathematical Recurrence Engine for Subscriptions (Phase 2, SPEC-003.1).
 * Enforces I-SUB-001 (cadence intervals), I-SUB-002 (confidence & population statistics),
 * I-SUB-003 (pre-observation baseline price spike), I-SUB-004 (cancellation & reactivation),
 * and I-SUB-007 (precision separation: scale 2 monetary, scale >= 6 statistics).
 */
@Component
public class RecurrencePatternEngine {

    private static final BigDecimal SPIKE_MULTIPLIER = new BigDecimal("1.05");
    private static final BigDecimal CV_FIXED_THRESHOLD = new BigDecimal("0.050000");
    private static final BigDecimal CV_CAP = new BigDecimal("0.500000");
    private static final BigDecimal ACTIVE_CONFIDENCE_THRESHOLD = new BigDecimal("0.700000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    public record CadenceResult(
            @NonNull Cadence cadence,
            double averageIntervalDays,
            @Nullable Instant nextExpectedAt
    ) {
        public CadenceResult {
            Objects.requireNonNull(cadence, "cadence cannot be null");
        }
    }

    public record PopulationStatistics(
            @NonNull BigDecimal mean,
            @NonNull BigDecimal stdDev,
            @NonNull BigDecimal cv,
            @NonNull VarianceType varianceType
    ) {
        public PopulationStatistics {
            Objects.requireNonNull(mean, "mean cannot be null");
            Objects.requireNonNull(stdDev, "stdDev cannot be null");
            Objects.requireNonNull(cv, "cv cannot be null");
            Objects.requireNonNull(varianceType, "varianceType cannot be null");
        }
    }

    public record PriceSpikeResult(
            @NonNull PriceState priceState,
            @NonNull Optional<SubscriptionPriceSpikeEvent> spikeEvent
    ) {
        public PriceSpikeResult {
            Objects.requireNonNull(priceState, "priceState cannot be null");
            Objects.requireNonNull(spikeEvent, "spikeEvent cannot be null");
        }
    }

    public record SubscriptionEvaluationResult(
            @NonNull Subscription subscription,
            @NonNull Optional<SubscriptionPriceSpikeEvent> spikeEvent
    ) {
        public SubscriptionEvaluationResult {
            Objects.requireNonNull(subscription, "subscription cannot be null");
            Objects.requireNonNull(spikeEvent, "spikeEvent cannot be null");
        }
    }

    /**
     * I-SUB-001: Evaluates cadence deterministically from observation timestamps.
     */
    public CadenceResult evaluateCadence(@NonNull final List<Instant> timestamps) {
        Objects.requireNonNull(timestamps, "timestamps cannot be null");
        if (timestamps.size() < 2) {
            return new CadenceResult(Cadence.IRREGULAR, 0.0, null);
        }

        int intervals = timestamps.size() - 1;
        Instant first = timestamps.getFirst();
        Instant last = timestamps.getLast();
        double totalDays = (double) Duration.between(first, last).toSeconds() / 86400.0;
        double avgIntervalDays = totalDays / intervals;

        return evaluateCadenceInterval(avgIntervalDays, last);
    }

    /**
     * Classifies cadence and computes nextExpectedAt based on average interval in days.
     */
    public CadenceResult evaluateCadenceInterval(final double avgIntervalDays, @NonNull final Instant lastObservedAt) {
        Objects.requireNonNull(lastObservedAt, "lastObservedAt cannot be null");

        Cadence cadence;
        if (Math.abs(avgIntervalDays - 7.0) <= 1.0) {
            cadence = Cadence.WEEKLY;
        } else if (Math.abs(avgIntervalDays - 14.0) <= 2.0) {
            cadence = Cadence.BI_WEEKLY;
        } else if (Math.abs(avgIntervalDays - 30.0) <= 3.0) {
            cadence = Cadence.MONTHLY;
        } else if (Math.abs(avgIntervalDays - 365.0) <= 5.0) {
            cadence = Cadence.ANNUAL;
        } else {
            cadence = Cadence.IRREGULAR;
        }

        Instant nextExpectedAt = null;
        if (cadence != Cadence.IRREGULAR) {
            long daysToAdd = Math.round(avgIntervalDays);
            nextExpectedAt = lastObservedAt.plus(daysToAdd, ChronoUnit.DAYS);
        }

        return new CadenceResult(cadence, avgIntervalDays, nextExpectedAt);
    }

    /**
     * I-SUB-002, I-SUB-007: Computes population statistics:
     * mean μ_A, population σ_A = sqrt(1/N * sum((A_i - μ)^2)), CV_A = σ_A / μ_A.
     */
    public PopulationStatistics calculatePopulationStatistics(@NonNull final List<BigDecimal> amounts) {
        Objects.requireNonNull(amounts, "amounts cannot be null");
        if (amounts.isEmpty()) {
            throw new IllegalArgumentException("Amounts list cannot be empty");
        }

        int n = amounts.size();
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal a : amounts) {
            sum = sum.add(a);
        }

        BigDecimal unscaledMean = sum.divide(BigDecimal.valueOf(n), MathContext.DECIMAL128);
        BigDecimal canonicalMean = unscaledMean.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (BigDecimal a : amounts) {
            BigDecimal diff = a.subtract(unscaledMean);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }

        BigDecimal populationVariance = sumSquaredDiff.divide(BigDecimal.valueOf(n), MathContext.DECIMAL128);
        BigDecimal stdDev = populationVariance.sqrt(MathContext.DECIMAL128).setScale(6, RoundingMode.HALF_EVEN);

        BigDecimal cv;
        if (unscaledMean.compareTo(BigDecimal.ZERO) == 0) {
            cv = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN);
        } else {
            cv = stdDev.divide(unscaledMean, 6, RoundingMode.HALF_EVEN);
        }

        VarianceType varianceType = cv.compareTo(CV_FIXED_THRESHOLD) <= 0 ? VarianceType.FIXED : VarianceType.VARIABLE;

        return new PopulationStatistics(canonicalMean, stdDev, cv, varianceType);
    }

    /**
     * I-SUB-002: Confidence(N, CV_A) = min(1.00, (N / 3) * (1.00 - min(0.50, CV_A))).
     */
    public BigDecimal calculateConfidence(final int n, @NonNull final BigDecimal cv) {
        Objects.requireNonNull(cv, "cv cannot be null");
        BigDecimal cappedCv = cv.min(CV_CAP);
        BigDecimal cvFactor = BigDecimal.ONE.setScale(6, RoundingMode.HALF_EVEN).subtract(cappedCv);
        BigDecimal nFactor = BigDecimal.valueOf(n).divide(BigDecimal.valueOf(3), MathContext.DECIMAL128);
        BigDecimal rawConfidence = nFactor.multiply(cvFactor, MathContext.DECIMAL128);
        return BigDecimal.ONE.setScale(6, RoundingMode.HALF_EVEN).min(rawConfidence.setScale(6, RoundingMode.HALF_EVEN));
    }

    /**
     * REQ-SUB-003, I-SUB-002: Evaluates lifecycle progression and reactivation:
     * - CANCELLED_INFERRED + new observation => CANDIDATE
     * - N=1 => DISCOVERED
     * - N=2 => CANDIDATE
     * - N >= 3 and Confidence >= 0.70 => ACTIVE, else CANDIDATE
     */
    public SubscriptionStatus evaluateStatus(
            final int n,
            @NonNull final BigDecimal confidence,
            @Nullable final SubscriptionStatus currentStatus
    ) {
        Objects.requireNonNull(confidence, "confidence cannot be null");
        if (currentStatus == SubscriptionStatus.CANCELLED_INFERRED) {
            return SubscriptionStatus.CANDIDATE;
        }
        if (n <= 1) {
            return SubscriptionStatus.DISCOVERED;
        }
        if (n == 2) {
            return SubscriptionStatus.CANDIDATE;
        }
        if (confidence.compareTo(ACTIVE_CONFIDENCE_THRESHOLD) >= 0) {
            return SubscriptionStatus.ACTIVE;
        }
        return SubscriptionStatus.CANDIDATE;
    }

    /**
     * I-SUB-003: Computes baseline mean strictly from preceding observations.
     */
    public BigDecimal calculateBaselineMean(@NonNull final List<BigDecimal> precedingAmounts) {
        Objects.requireNonNull(precedingAmounts, "precedingAmounts cannot be null");
        if (precedingAmounts.isEmpty()) {
            throw new IllegalArgumentException("Preceding amounts list cannot be empty");
        }
        BigDecimal sum = precedingAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(precedingAmounts.size()), 2, RoundingMode.HALF_EVEN);
    }

    /**
     * I-SUB-003: Price spike evaluation:
     * A_new >= μ_A(t) * 1.05 triggers PRICE_SPIKE_DETECTED and emits SubscriptionPriceSpikeEvent.
     */
    public PriceSpikeResult evaluatePriceSpike(
            @NonNull final UUID subscriptionId,
            @NonNull final String tenantId,
            @NonNull final UUID walletId,
            @NonNull final UUID counterpartyId,
            @Nullable final BigDecimal baselineMean,
            @NonNull final BigDecimal newAmount,
            @NonNull final UUID triggerEventId,
            @NonNull final Instant detectedAt
    ) {
        Objects.requireNonNull(subscriptionId, "subscriptionId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(counterpartyId, "counterpartyId cannot be null");
        Objects.requireNonNull(newAmount, "newAmount cannot be null");
        Objects.requireNonNull(triggerEventId, "triggerEventId cannot be null");
        Objects.requireNonNull(detectedAt, "detectedAt cannot be null");

        if (baselineMean == null) {
            return new PriceSpikeResult(PriceState.NORMAL, Optional.empty());
        }

        BigDecimal spikeThreshold = baselineMean.multiply(SPIKE_MULTIPLIER).setScale(6, RoundingMode.HALF_EVEN);
        if (newAmount.compareTo(spikeThreshold) >= 0) {
            BigDecimal diff = newAmount.subtract(baselineMean);
            BigDecimal percentageIncrease = diff.divide(baselineMean, 4, RoundingMode.HALF_EVEN)
                    .multiply(ONE_HUNDRED)
                    .setScale(2, RoundingMode.HALF_EVEN);

            var event = new SubscriptionPriceSpikeEvent(
                    subscriptionId,
                    tenantId,
                    walletId,
                    counterpartyId,
                    baselineMean.setScale(2, RoundingMode.HALF_EVEN),
                    newAmount.setScale(2, RoundingMode.HALF_EVEN),
                    percentageIncrease,
                    triggerEventId,
                    detectedAt
            );
            return new PriceSpikeResult(PriceState.PRICE_SPIKE_DETECTED, Optional.of(event));
        }

        return new PriceSpikeResult(PriceState.NORMAL, Optional.empty());
    }

    /**
     * I-SUB-004, REQ-SUB-009: Lapse condition:
     * For ACTIVE non-IRREGULAR subscriptions, elapsed > 1.50 * Δt transitions to CANCELLED_INFERRED.
     */
    public boolean shouldInferCancellation(
            @NonNull final SubscriptionStatus currentStatus,
            @NonNull final Cadence cadence,
            final double avgIntervalDays,
            @NonNull final Instant lastObservedAt,
            @NonNull final Instant now
    ) {
        if (currentStatus != SubscriptionStatus.ACTIVE || cadence == Cadence.IRREGULAR || avgIntervalDays <= 0) {
            return false;
        }
        double elapsedDays = (double) Duration.between(lastObservedAt, now).toSeconds() / 86400.0;
        return elapsedDays > (1.50 * avgIntervalDays);
    }

    /**
     * Pure engine processing of an observation against existing subscription projection.
     */
    public SubscriptionEvaluationResult processObservation(
            @NonNull final Optional<Subscription> existingSubscription,
            @NonNull final String tenantId,
            @NonNull final UUID walletId,
            @NonNull final UUID counterpartyId,
            @NonNull final BigDecimal amount,
            @NonNull final Instant timestamp,
            @NonNull final UUID eventId
    ) {
        Objects.requireNonNull(existingSubscription, "existingSubscription cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(walletId, "walletId cannot be null");
        Objects.requireNonNull(counterpartyId, "counterpartyId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");

        if (existingSubscription.isEmpty()) {
            UUID id = UUID.randomUUID();
            BigDecimal canonicalAmount = amount.setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal confidence = calculateConfidence(1, BigDecimal.ZERO);
            Subscription sub = new Subscription(
                    id,
                    tenantId,
                    walletId,
                    counterpartyId,
                    Cadence.IRREGULAR,
                    SubscriptionStatus.DISCOVERED,
                    PriceState.NORMAL,
                    "UNKNOWN",
                    canonicalAmount,
                    canonicalAmount,
                    confidence,
                    1,
                    VarianceType.FIXED,
                    null,
                    timestamp,
                    timestamp,
                    timestamp
            );
            return new SubscriptionEvaluationResult(sub, Optional.empty());
        }

        Subscription prev = existingSubscription.get();
        int n = prev.observedCycles() + 1;
        BigDecimal baselineMean = prev.averageAmount();

        PriceSpikeResult spikeResult = evaluatePriceSpike(
                prev.id(), tenantId, walletId, counterpartyId,
                baselineMean, amount, eventId, timestamp
        );

        BigDecimal prevSum = baselineMean.multiply(BigDecimal.valueOf(prev.observedCycles()));
        BigDecimal newSum = prevSum.add(amount);
        BigDecimal newMean = newSum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_EVEN);

        double totalDays = (double) Duration.between(prev.createdAt(), timestamp).toSeconds() / 86400.0;
        double avgIntervalDays = totalDays / (n - 1);
        CadenceResult cadenceResult = evaluateCadenceInterval(avgIntervalDays, timestamp);

        BigDecimal cv;
        VarianceType varianceType;
        if (amount.compareTo(baselineMean) == 0 && prev.varianceType() == VarianceType.FIXED) {
            cv = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN);
            varianceType = VarianceType.FIXED;
        } else {
            // Streaming variance update
            BigDecimal diff = amount.subtract(baselineMean);
            BigDecimal factor = BigDecimal.valueOf(n - 1).divide(BigDecimal.valueOf((long) n * n), MathContext.DECIMAL128);
            BigDecimal variance = factor.multiply(diff.multiply(diff));
            BigDecimal stdDev = variance.sqrt(MathContext.DECIMAL128).setScale(6, RoundingMode.HALF_EVEN);
            cv = stdDev.divide(newMean, 6, RoundingMode.HALF_EVEN);
            varianceType = cv.compareTo(CV_FIXED_THRESHOLD) <= 0 ? VarianceType.FIXED : VarianceType.VARIABLE;
        }

        BigDecimal confidence = calculateConfidence(n, cv);
        SubscriptionStatus status = evaluateStatus(n, confidence, prev.status());
        PriceState priceState = spikeResult.priceState() == PriceState.PRICE_SPIKE_DETECTED
                ? PriceState.PRICE_SPIKE_DETECTED
                : prev.priceState();

        Subscription updated = new Subscription(
                prev.id(),
                tenantId,
                walletId,
                counterpartyId,
                cadenceResult.cadence(),
                status,
                priceState,
                prev.classification(),
                newMean,
                amount.setScale(2, RoundingMode.HALF_EVEN),
                confidence,
                n,
                varianceType,
                cadenceResult.nextExpectedAt(),
                timestamp,
                prev.createdAt(),
                timestamp
        );

        return new SubscriptionEvaluationResult(updated, spikeResult.spikeEvent());
    }
}
