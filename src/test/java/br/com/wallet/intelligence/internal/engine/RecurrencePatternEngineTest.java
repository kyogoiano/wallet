package br.com.wallet.intelligence.internal.engine;

import br.com.wallet.intelligence.api.event.SubscriptionPriceSpikeEvent;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RecurrencePatternEngine Unit Tests (Phase 2, REQ-SUB-003..006, I-SUB-001..004, I-SUB-007)")
class RecurrencePatternEngineTest {

    private RecurrencePatternEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecurrencePatternEngine();
    }

    @Nested
    @DisplayName("TASK-3.1.4: Cadence Intervals & Projected Renewal (REQ-SUB-004, I-SUB-001)")
    class CadenceIntervalsTests {

        private final Instant t0 = Instant.parse("2026-01-01T10:00:00Z");

        @Test
        @DisplayName("Positive Canonical: Weekly cadence detection (|Δt - 7| <= 1 day)")
        void shouldClassifyWeeklyCadence() {
            // Delta = 7 days
            List<Instant> timestamps = List.of(
                    t0,
                    t0.plus(7, ChronoUnit.DAYS),
                    t0.plus(14, ChronoUnit.DAYS)
            );

            var result = engine.evaluateCadence(timestamps);

            assertThat(result.cadence()).isEqualTo(Cadence.WEEKLY);
            assertThat(result.averageIntervalDays()).isCloseTo(7.0, within(0.001));
            assertThat(result.nextExpectedAt()).isEqualTo(t0.plus(21, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("Boundary: Weekly cadence at limits (6 days and 8 days)")
        void shouldClassifyWeeklyCadenceAtBoundaries() {
            // Delta = 6 days (lower bound: |6 - 7| = 1 <= 1)
            var lower = engine.evaluateCadence(List.of(t0, t0.plus(6, ChronoUnit.DAYS)));
            assertThat(lower.cadence()).isEqualTo(Cadence.WEEKLY);
            assertThat(lower.nextExpectedAt()).isEqualTo(t0.plus(12, ChronoUnit.DAYS));

            // Delta = 8 days (upper bound: |8 - 7| = 1 <= 1)
            var upper = engine.evaluateCadence(List.of(t0, t0.plus(8, ChronoUnit.DAYS)));
            assertThat(upper.cadence()).isEqualTo(Cadence.WEEKLY);
            assertThat(upper.nextExpectedAt()).isEqualTo(t0.plus(16, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("Positive Canonical: Bi-weekly cadence detection (|Δt - 14| <= 2 days)")
        void shouldClassifyBiWeeklyCadence() {
            var result = engine.evaluateCadence(List.of(
                    t0,
                    t0.plus(14, ChronoUnit.DAYS),
                    t0.plus(28, ChronoUnit.DAYS)
            ));

            assertThat(result.cadence()).isEqualTo(Cadence.BI_WEEKLY);
            assertThat(result.averageIntervalDays()).isCloseTo(14.0, within(0.001));
            assertThat(result.nextExpectedAt()).isEqualTo(t0.plus(42, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("Boundary: Bi-weekly at limits (12 days and 16 days)")
        void shouldClassifyBiWeeklyAtBoundaries() {
            var lower = engine.evaluateCadence(List.of(t0, t0.plus(12, ChronoUnit.DAYS)));
            assertThat(lower.cadence()).isEqualTo(Cadence.BI_WEEKLY);

            var upper = engine.evaluateCadence(List.of(t0, t0.plus(16, ChronoUnit.DAYS)));
            assertThat(upper.cadence()).isEqualTo(Cadence.BI_WEEKLY);
        }

        @Test
        @DisplayName("Positive Canonical: Monthly cadence detection (|Δt - 30| <= 3 days)")
        void shouldClassifyMonthlyCadence() {
            var result = engine.evaluateCadence(List.of(
                    t0,
                    t0.plus(30, ChronoUnit.DAYS),
                    t0.plus(60, ChronoUnit.DAYS)
            ));

            assertThat(result.cadence()).isEqualTo(Cadence.MONTHLY);
            assertThat(result.averageIntervalDays()).isCloseTo(30.0, within(0.001));
            assertThat(result.nextExpectedAt()).isEqualTo(t0.plus(90, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("Boundary: Monthly at limits (27 days and 33 days)")
        void shouldClassifyMonthlyAtBoundaries() {
            var lower = engine.evaluateCadence(List.of(t0, t0.plus(27, ChronoUnit.DAYS)));
            assertThat(lower.cadence()).isEqualTo(Cadence.MONTHLY);

            var upper = engine.evaluateCadence(List.of(t0, t0.plus(33, ChronoUnit.DAYS)));
            assertThat(upper.cadence()).isEqualTo(Cadence.MONTHLY);
        }

        @Test
        @DisplayName("Positive Canonical: Annual cadence detection (|Δt - 365| <= 5 days)")
        void shouldClassifyAnnualCadence() {
            var result = engine.evaluateCadence(List.of(
                    t0,
                    t0.plus(365, ChronoUnit.DAYS)
            ));

            assertThat(result.cadence()).isEqualTo(Cadence.ANNUAL);
            assertThat(result.averageIntervalDays()).isCloseTo(365.0, within(0.001));
            assertThat(result.nextExpectedAt()).isEqualTo(t0.plus(730, ChronoUnit.DAYS));
        }

        @Test
        @DisplayName("Boundary / Invariant Breach: Irregular cadence returns null nextExpectedAt")
        void shouldClassifyIrregularCadenceAndNullNextExpectedAt() {
            // Delta = 20 days (outside weekly, bi-weekly, monthly, annual)
            var irregular = engine.evaluateCadence(List.of(t0, t0.plus(20, ChronoUnit.DAYS)));
            assertThat(irregular.cadence()).isEqualTo(Cadence.IRREGULAR);
            assertThat(irregular.nextExpectedAt()).isNull();

            // Single observation has no delta
            var single = engine.evaluateCadence(List.of(t0));
            assertThat(single.cadence()).isEqualTo(Cadence.IRREGULAR);
            assertThat(single.nextExpectedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("TASK-3.1.5: Population Statistics & Amount Variance (REQ-SUB-005, I-SUB-002, I-SUB-007)")
    class PopulationStatisticsTests {

        @Test
        @DisplayName("Positive Canonical: Fixed variance (CV_A == 0) for identical amounts")
        void shouldCalculateZeroVarianceForIdenticalAmounts() {
            List<BigDecimal> amounts = List.of(
                    new BigDecimal("49.90"),
                    new BigDecimal("49.90"),
                    new BigDecimal("49.90")
            );

            var stats = engine.calculatePopulationStatistics(amounts);

            assertThat(stats.mean()).isEqualByComparingTo(new BigDecimal("49.90"));
            assertThat(stats.stdDev()).isEqualByComparingTo(new BigDecimal("0.000000"));
            assertThat(stats.cv()).isEqualByComparingTo(new BigDecimal("0.000000"));
            assertThat(stats.varianceType()).isEqualTo(VarianceType.FIXED);
        }

        @Test
        @DisplayName("Boundary Gate: Fixed variance classification when CV_A <= 0.05")
        void shouldClassifyAsFixedWhenCvWithinThreshold() {
            // Mean = 100.00, amounts = [98.00, 100.00, 102.00]
            // sum of squared diffs = (-2)^2 + 0 + 2^2 = 8
            // population var = 8/3 = 2.666667
            // stdDev = sqrt(8/3) ~= 1.632993
            // CV = 1.632993 / 100.00 ~= 0.016330 <= 0.05
            List<BigDecimal> amounts = List.of(
                    new BigDecimal("98.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("102.00")
            );

            var stats = engine.calculatePopulationStatistics(amounts);

            assertThat(stats.mean()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(stats.cv()).isLessThanOrEqualTo(new BigDecimal("0.050000"));
            assertThat(stats.varianceType()).isEqualTo(VarianceType.FIXED);
        }

        @Test
        @DisplayName("Invariant Breach Gate: Variable variance classification when CV_A > 0.05")
        void shouldClassifyAsVariableWhenCvExceedsThreshold() {
            // Mean = 100.00, amounts = [80.00, 100.00, 120.00]
            // sum of sq diffs = (-20)^2 + 0 + (20)^2 = 800
            // population var = 800 / 3 = 266.666667
            // stdDev = sqrt(266.666667) ~= 16.329932
            // CV = 16.329932 / 100.00 = 0.163299 > 0.05
            List<BigDecimal> amounts = List.of(
                    new BigDecimal("80.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("102.00") // Mean = 94.00, CV > 0.05
            );

            var stats = engine.calculatePopulationStatistics(amounts);

            assertThat(stats.varianceType()).isEqualTo(VarianceType.VARIABLE);
            assertThat(stats.cv()).isGreaterThan(new BigDecimal("0.050000"));
        }
    }

    @Nested
    @DisplayName("TASK-3.1.6: Confidence Score & Lifecycle Boundary Gates (REQ-SUB-003, I-SUB-002)")
    class ConfidenceAndLifecycleGatesTests {

        @Test
        @DisplayName("Exact Triad 1: N=3, CV=0 => Confidence=1.00 => ACTIVE")
        void shouldEvaluateActiveWhenN3AndCvZero() {
            BigDecimal cv = new BigDecimal("0.000000");
            int n = 3;

            BigDecimal confidence = engine.calculateConfidence(n, cv);
            SubscriptionStatus status = engine.evaluateStatus(n, confidence, SubscriptionStatus.CANDIDATE);

            assertThat(confidence).isEqualByComparingTo(new BigDecimal("1.000000"));
            assertThat(status).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("Exact Triad 2: N=3, CV=0.30 => Confidence=0.70 => ACTIVE")
        void shouldEvaluateActiveAtExactBoundaryN3AndCv30Percent() {
            // Confidence = 3/3 * (1.00 - min(0.50, 0.30)) = 1.00 - 0.30 = 0.70
            BigDecimal cv = new BigDecimal("0.300000");
            int n = 3;

            BigDecimal confidence = engine.calculateConfidence(n, cv);
            SubscriptionStatus status = engine.evaluateStatus(n, confidence, SubscriptionStatus.CANDIDATE);

            assertThat(confidence).isEqualByComparingTo(new BigDecimal("0.700000"));
            assertThat(status).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("Exact Triad 3: N=3, CV=0.31 (>0.30) => Confidence < 0.70 => CANDIDATE")
        void shouldRemainCandidateWhenCvExceeds30Percent() {
            // Confidence = 3/3 * (1.00 - 0.31) = 0.69 < 0.70
            BigDecimal cv = new BigDecimal("0.310000");
            int n = 3;

            BigDecimal confidence = engine.calculateConfidence(n, cv);
            SubscriptionStatus status = engine.evaluateStatus(n, confidence, SubscriptionStatus.CANDIDATE);

            assertThat(confidence).isEqualByComparingTo(new BigDecimal("0.690000"));
            assertThat(status).isEqualTo(SubscriptionStatus.CANDIDATE);
        }

        @Test
        @DisplayName("Exact Lifecycle Progression: N=1 => DISCOVERED, N=2 => CANDIDATE")
        void shouldEnforceLifecycleProgressionByObservationCount() {
            // N=1: even with CV=0, confidence = 1/3 * 1.0 = 0.333333
            BigDecimal conf1 = engine.calculateConfidence(1, BigDecimal.ZERO);
            SubscriptionStatus status1 = engine.evaluateStatus(1, conf1, null);
            assertThat(status1).isEqualTo(SubscriptionStatus.DISCOVERED);

            // N=2: even with CV=0, confidence = 2/3 * 1.0 = 0.666667
            BigDecimal conf2 = engine.calculateConfidence(2, BigDecimal.ZERO);
            SubscriptionStatus status2 = engine.evaluateStatus(2, conf2, SubscriptionStatus.DISCOVERED);
            assertThat(status2).isEqualTo(SubscriptionStatus.CANDIDATE);
        }
    }

    @Nested
    @DisplayName("TASK-3.1.7: Historical Baseline Price Spike Detection (REQ-SUB-006, I-SUB-003)")
    class PriceSpikeDetectionTests {

        private final UUID subscriptionId = UUID.randomUUID();
        private final String tenantId = "tenant-alpha";
        private final UUID walletId = UUID.randomUUID();
        private final UUID counterpartyId = UUID.randomUUID();
        private final UUID triggerEventId = UUID.randomUUID();
        private final Instant now = Instant.now();

        @Test
        @DisplayName("Positive Canonical: +15% increase triggers PRICE_SPIKE_DETECTED while status remains ACTIVE")
        void shouldTriggerPriceSpikeOn15PercentIncrease() {
            BigDecimal baselineMean = new BigDecimal("100.00");
            BigDecimal newAmount = new BigDecimal("115.00"); // +15%

            var result = engine.evaluatePriceSpike(
                    subscriptionId, tenantId, walletId, counterpartyId,
                    baselineMean, newAmount, triggerEventId, now
            );

            assertThat(result.priceState()).isEqualTo(PriceState.PRICE_SPIKE_DETECTED);
            assertThat(result.spikeEvent()).isPresent();

            SubscriptionPriceSpikeEvent event = result.spikeEvent().get();
            assertThat(event.subscriptionId()).isEqualTo(subscriptionId);
            assertThat(event.tenantId()).isEqualTo(tenantId);
            assertThat(event.walletId()).isEqualTo(walletId);
            assertThat(event.counterpartyId()).isEqualTo(counterpartyId);
            assertThat(event.baselineAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(event.observedAmount()).isEqualByComparingTo(new BigDecimal("115.00"));
            assertThat(event.percentageIncrease()).isEqualByComparingTo(new BigDecimal("15.00"));
            assertThat(event.triggerEventId()).isEqualTo(triggerEventId);
        }

        @Test
        @DisplayName("Boundary Gate: Exactly +5.00% triggers PRICE_SPIKE_DETECTED")
        void shouldTriggerPriceSpikeAtExact5PercentBoundary() {
            BigDecimal baselineMean = new BigDecimal("100.00");
            BigDecimal newAmount = new BigDecimal("105.00"); // Exactly +5.00%

            var result = engine.evaluatePriceSpike(
                    subscriptionId, tenantId, walletId, counterpartyId,
                    baselineMean, newAmount, triggerEventId, now
            );

            assertThat(result.priceState()).isEqualTo(PriceState.PRICE_SPIKE_DETECTED);
            assertThat(result.spikeEvent()).isPresent();
            assertThat(result.spikeEvent().get().percentageIncrease()).isEqualByComparingTo(new BigDecimal("5.00"));
        }

        @Test
        @DisplayName("Negative Gate: < 5.00% increase remains NORMAL without event emission")
        void shouldRemainNormalWhenPriceIncreaseBelow5Percent() {
            BigDecimal baselineMean = new BigDecimal("100.00");
            BigDecimal newAmount = new BigDecimal("104.99"); // +4.99%

            var result = engine.evaluatePriceSpike(
                    subscriptionId, tenantId, walletId, counterpartyId,
                    baselineMean, newAmount, triggerEventId, now
            );

            assertThat(result.priceState()).isEqualTo(PriceState.NORMAL);
            assertThat(result.spikeEvent()).isEmpty();
        }

        @Test
        @DisplayName("Invariant Assertion: Baseline mean excludes A_new")
        void shouldCalculateBaselineExclusivelyFromPrecedingObservations() {
            // If historical amounts were [100.00, 100.00, 100.00]
            // baseline = 100.00
            // If new amount is 115.00, baseline is 100.00 (not 103.75)
            List<BigDecimal> precedingAmounts = List.of(
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("100.00")
            );
            BigDecimal newAmount = new BigDecimal("115.00");

            BigDecimal baselineMean = engine.calculateBaselineMean(precedingAmounts);
            assertThat(baselineMean).isEqualByComparingTo(new BigDecimal("100.00"));

            var result = engine.evaluatePriceSpike(
                    subscriptionId, tenantId, walletId, counterpartyId,
                    baselineMean, newAmount, triggerEventId, now
            );
            assertThat(result.priceState()).isEqualTo(PriceState.PRICE_SPIKE_DETECTED);
        }
    }

    @Nested
    @DisplayName("TASK-3.1.14 (Preview): Cancellation Inference & Reactivation (REQ-SUB-009, I-SUB-004)")
    class CancellationAndReactivationTests {

        private final Instant lastObserved = Instant.parse("2026-01-01T10:00:00Z");

        @Test
        @DisplayName("Elapsed > 1.50 * Δt transitions ACTIVE non-IRREGULAR subscription to CANCELLED_INFERRED")
        void shouldInferCancellationWhenElapsedExceedsThreshold() {
            // Monthly = 30 days. 1.5 * 30 = 45 days.
            // Elapsed = 46 days (> 45 days)
            Instant now = lastObserved.plus(46, ChronoUnit.DAYS);

            boolean cancelled = engine.shouldInferCancellation(SubscriptionStatus.ACTIVE, Cadence.MONTHLY, 30.0, lastObserved, now);
            assertThat(cancelled).isTrue();
        }

        @Test
        @DisplayName("Elapsed <= 1.50 * Δt remains ACTIVE")
        void shouldNotInferCancellationWithinThreshold() {
            // Elapsed = 44 days (<= 45 days)
            Instant now = lastObserved.plus(44, ChronoUnit.DAYS);

            boolean cancelled = engine.shouldInferCancellation(SubscriptionStatus.ACTIVE, Cadence.MONTHLY, 30.0, lastObserved, now);
            assertThat(cancelled).isFalse();
        }

        @Test
        @DisplayName("IRREGULAR subscription is never inferred cancelled")
        void shouldNeverInferCancellationForIrregularSubscription() {
            Instant now = lastObserved.plus(200, ChronoUnit.DAYS);

            boolean cancelled = engine.shouldInferCancellation(SubscriptionStatus.ACTIVE, Cadence.IRREGULAR, 0.0, lastObserved, now);
            assertThat(cancelled).isFalse();
        }

        @Test
        @DisplayName("New observation on CANCELLED_INFERRED reactivates to CANDIDATE")
        void shouldReactivateCancelledSubscriptionToCandidate() {
            SubscriptionStatus next = engine.evaluateStatus(4, new BigDecimal("1.000000"), SubscriptionStatus.CANCELLED_INFERRED);
            assertThat(next).isEqualTo(SubscriptionStatus.CANDIDATE);
        }
    }
}
