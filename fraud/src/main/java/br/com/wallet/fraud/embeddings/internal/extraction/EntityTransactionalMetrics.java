package br.com.wallet.fraud.embeddings.internal.extraction;

/**
 * Aggregated transactional and authentication statistics for an entity over a 30-day window.
 */
public record EntityTransactionalMetrics(
    long txCount30d,
    double avgOutgoingAmount,
    double amountStdDev,
    long maxVelocity1h,
    long nocturnalTxCount,
    long uniqueCounterparties,
    long newCounterparties7d,
    long rapidDrainCount,
    long distinctDestinations,
    long totalSentCount,
    long distinctSources,
    long totalReceivedCount,
    long internationalTxCount,
    long failedAuthCount,
    long deviceSwitchCount,
    long outOfPatternCount,
    long disputeCount,
    double totalOutgoingVolume
) {
    public static EntityTransactionalMetrics empty() {
        return new EntityTransactionalMetrics(
            0, 0.0, 0.0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0
        );
    }
}
