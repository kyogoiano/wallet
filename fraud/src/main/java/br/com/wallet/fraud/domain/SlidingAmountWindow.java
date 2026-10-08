package br.com.wallet.fraud.domain;

import java.util.concurrent.atomic.LongAdder;

public final class SlidingAmountWindow {

    private final long[] bucketAmounts;
    private final long[] bucketSeconds;

    private final int windowSizeSeconds;

    private final LongAdder totalAmount = new LongAdder();

    private long latestSecond;

    public SlidingAmountWindow(final int windowSizeSeconds) {
        if (windowSizeSeconds <= 0) {
            throw new IllegalArgumentException("Window size must be positive.");
        }

        this.windowSizeSeconds = windowSizeSeconds;
        this.bucketAmounts = new long[windowSizeSeconds];
        this.bucketSeconds = new long[windowSizeSeconds];
    }

    public synchronized void add(final long timestampMs, final long amount) {

        final long second = timestampMs / 1000;

        advanceWindow(second);

        final int index = (int) (second % windowSizeSeconds);

        if (bucketSeconds[index] != second) {

            if (bucketAmounts[index] != 0) {
                totalAmount.add(-bucketAmounts[index]);
            }

            bucketAmounts[index] = 0;
            bucketSeconds[index] = second;
        }

        bucketAmounts[index] += amount;
        totalAmount.add(amount);
    }

    private void advanceWindow(final long second) {

        if (second <= latestSecond) {
            return;
        }

        long gap = second - latestSecond;

        if (gap >= windowSizeSeconds) {

            for (int i = 0; i < windowSizeSeconds; i++) {
                if (bucketAmounts[i] != 0) {
                    totalAmount.add(-bucketAmounts[i]);
                    bucketAmounts[i] = 0;
                    bucketSeconds[i] = 0;
                }
            }

        } else {

            for (long s = latestSecond + 1; s <= second; s++) {

                int index = (int) (s % windowSizeSeconds);

                if (bucketSeconds[index] != 0 &&
                        s - bucketSeconds[index] >= windowSizeSeconds) {

                    totalAmount.add(-bucketAmounts[index]);
                    bucketAmounts[index] = 0;
                    bucketSeconds[index] = 0;
                }
            }
        }

        latestSecond = second;
    }

    public long total() {
        return totalAmount.sum();
    }
}