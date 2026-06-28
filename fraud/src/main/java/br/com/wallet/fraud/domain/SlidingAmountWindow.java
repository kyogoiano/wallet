package br.com.wallet.fraud.domain;

public class SlidingAmountWindow {

    private final long[] bucketAmounts;
    private final long[] bucketTimestamps;

    private final int windowSizeSeconds;

    private volatile long totalAmount;
    private long latestObservedSecond;

    /**
     * fixed-size ring buffer indexed by epoch second
     * O(1) memory and almost O(1) updates
     * Eventual consistency due to volatile amount
     * @param windowSizeSeconds window size in seconds
     */
    public SlidingAmountWindow(final int windowSizeSeconds) {
        if (windowSizeSeconds <= 0) {
            throw new IllegalArgumentException("Window size must be positive.");
        }
        this.windowSizeSeconds = windowSizeSeconds;

        this.bucketAmounts = new long[windowSizeSeconds];
        this.bucketTimestamps = new long[windowSizeSeconds];
    }

    private void cleanup(long currentSecond) {
        for (int i = 0; i < windowSizeSeconds; i++) {
            if (bucketTimestamps[i] != 0 && (currentSecond - bucketTimestamps[i] >= windowSizeSeconds)) {
                totalAmount -= bucketAmounts[i];
                bucketAmounts[i] = 0;
                bucketTimestamps[i] = 0;
            }
        }
    }

    public synchronized void add(final long timestampMs, final long amount) {
        long second = timestampMs / 1000;
        if (second > latestObservedSecond) {
            latestObservedSecond = second;
        }
        cleanup(latestObservedSecond);

        int index = (int) (second % windowSizeSeconds);

        if (bucketTimestamps[index] == 0) {
            bucketTimestamps[index] = second;
        } else if (bucketTimestamps[index] != second) {
            totalAmount -= bucketAmounts[index];
            bucketAmounts[index] = 0;
            bucketTimestamps[index] = second;
        }

        bucketAmounts[index] += amount;
        totalAmount += amount;
    }

    public long total() {
        return totalAmount;
    }
}
