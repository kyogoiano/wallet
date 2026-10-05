package br.com.wallet.dlq.internal.engine;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Pure full-jitter exponential backoff calculator (REQ-TDLQ-005, I-TDLQ-003).
 * Calculates Delta t_r = Uniform(0, min(60s, 2s * 2^r)).
 */
public final class FullJitterBackoffCalculator {

    private static final long BASE_DELAY_SECONDS = 2L;
    private static final long MAX_DELAY_SECONDS = 60L;

    private FullJitterBackoffCalculator() {
    }

    /**
     * Calculates the exponential backoff ceiling for a given retry count.
     *
     * @param retryCount zero-based retry count (r >= 0)
     * @return ceiling in seconds capped at 60s
     */
    public static long calculateCeilingSeconds(final int retryCount) {
        final int boundedRetries = Math.max(0, retryCount);
        if (boundedRetries >= 6) {
            return MAX_DELAY_SECONDS;
        }
        return Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * (1L << boundedRetries));
    }

    /**
     * Calculates a jittered delay using ThreadLocalRandom.
     *
     * @param retryCount zero-based retry count
     * @return duration between 0 and ceiling
     */
    public static Duration calculateDelay(final int retryCount) {
        return calculateDelay(retryCount, ThreadLocalRandom.current());
    }

    /**
     * Calculates a jittered delay using the supplied RandomGenerator.
     *
     * @param retryCount zero-based retry count
     * @param random random generator
     * @return duration between 0 and ceiling (inclusive)
     */
    public static Duration calculateDelay(final int retryCount, final RandomGenerator random) {
        final long ceiling = calculateCeilingSeconds(retryCount);
        final long delaySeconds = random.nextLong(0, ceiling + 1);
        return Duration.ofSeconds(delaySeconds);
    }
}
