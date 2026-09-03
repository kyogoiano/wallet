package br.com.wallet.fraud.embeddings.internal.extraction;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Extracts 16 normalized transactional metrics classified across 7 semantic domains.
 */
@Component
public class FeatureVectorExtractor {

    public double @NonNull [] extractRawFeatures(@NonNull final EntityTransactionalMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics cannot be null");
        double[] features = new double[16];

        // Group 1: Transactional Behavior
        features[0] = clamp(metrics.txCount30d() / 500.0);
        features[1] = clamp(metrics.avgOutgoingAmount() / 50000.0);
        features[2] = clamp(metrics.amountStdDev() / 25000.0);
        features[3] = clamp(metrics.maxVelocity1h() / 30.0);
        features[4] = metrics.txCount30d() > 0 ? clamp((double) metrics.nocturnalTxCount() / metrics.txCount30d()) : 0.0;

        // Group 2: Counterparty Behavior
        features[5] = clamp(metrics.uniqueCounterparties() / 100.0);
        features[6] = clamp(metrics.newCounterparties7d() / 50.0);

        // Group 3: Flow Behavior
        features[7] = clamp(metrics.rapidDrainCount() / 20.0);
        features[8] = metrics.totalSentCount() > 0 ? clamp((double) metrics.distinctDestinations() / metrics.totalSentCount()) : 0.0;
        features[9] = metrics.totalReceivedCount() > 0 ? clamp((double) metrics.distinctSources() / metrics.totalReceivedCount()) : 0.0;

        // Group 4: Geographic / Context
        features[10] = clamp(metrics.internationalTxCount() / 10.0);

        // Group 5: Authentication / Device
        features[11] = clamp(metrics.failedAuthCount() / 10.0);
        features[12] = clamp(metrics.deviceSwitchCount() / 5.0);

        // Group 6: Statistical Anomaly
        features[13] = metrics.txCount30d() > 0 ? clamp((double) metrics.outOfPatternCount() / metrics.txCount30d()) : 0.0;

        // Group 7: Dispute & Volume
        features[14] = clamp(metrics.disputeCount() / 5.0);
        features[15] = clamp(metrics.totalOutgoingVolume() / 200000.0);

        return features;
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        return Math.min(value, 1.0);
    }
}
