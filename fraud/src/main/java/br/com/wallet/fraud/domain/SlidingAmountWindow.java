package br.com.wallet.fraud.domain;

public class SlidingAmountWindow {

    private final long[] bucketAmounts;
    private final long[] bucketTimestamps;

    private final int windowSizeSeconds;

    private volatile long totalAmount;

    public SlidingAmountWindow(final int windowSizeSeconds) {

        this.windowSizeSeconds = windowSizeSeconds;

        this.bucketAmounts = new long[windowSizeSeconds];
        this.bucketTimestamps = new long[windowSizeSeconds];
    }

    public synchronized void add(final long timestampMs, final long amount) {

        long second = timestampMs / 1000;

        int index = (int) (second % windowSizeSeconds);

        // bucket expired
        if (bucketTimestamps[index] != second) {

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
