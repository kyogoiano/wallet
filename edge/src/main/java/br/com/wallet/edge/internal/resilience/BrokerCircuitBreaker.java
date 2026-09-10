package br.com.wallet.edge.internal.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker guarding primary broker (NATS JetStream) connectivity (REQ-EDG-006).
 * Trips to OPEN when error rate > 10% or latency > 50ms over a sliding window.
 */
public class BrokerCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static final double DEFAULT_FAILURE_RATE_THRESHOLD = 0.10; // 10%
    public static final long DEFAULT_SLOW_CALL_NANOS = 50_000_000L; // 50ms
    public static final long DEFAULT_WAIT_DURATION_IN_OPEN_NANOS = 5_000_000_000L; // 5 seconds
    public static final int DEFAULT_RING_BUFFER_SIZE = 50;

    private final double failureRateThreshold;
    private final long slowCallDurationNanos;
    private final long waitDurationInOpenNanos;
    private final int ringBufferSize;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong lastStateTransitionNanos = new AtomicLong(System.nanoTime());

    private final AtomicInteger totalCalls = new AtomicInteger(0);
    private final AtomicInteger failedOrSlowCalls = new AtomicInteger(0);

    public BrokerCircuitBreaker() {
        this(DEFAULT_FAILURE_RATE_THRESHOLD, DEFAULT_SLOW_CALL_NANOS, DEFAULT_WAIT_DURATION_IN_OPEN_NANOS, DEFAULT_RING_BUFFER_SIZE);
    }

    public BrokerCircuitBreaker(double failureRateThreshold, long slowCallDurationNanos, long waitDurationInOpenNanos, int ringBufferSize) {
        this.failureRateThreshold = failureRateThreshold;
        this.slowCallDurationNanos = slowCallDurationNanos;
        this.waitDurationInOpenNanos = waitDurationInOpenNanos;
        this.ringBufferSize = ringBufferSize;
    }

    public boolean isCallPermitted() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }

        long now = System.nanoTime();
        if (current == State.OPEN) {
            if (now - lastStateTransitionNanos.get() >= waitDurationInOpenNanos) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    lastStateTransitionNanos.set(now);
                    resetCounters();
                    return true;
                }
            }
            return false;
        }

        // HALF_OPEN
        return true;
    }

    public void recordSuccess(long durationNanos) {
        if (durationNanos > slowCallDurationNanos) {
            recordFailureOrSlow();
        } else {
            recordSuccessCall();
        }
    }

    public void recordFailure(Throwable t) {
        recordFailureOrSlow();
    }

    private void recordSuccessCall() {
        int calls = totalCalls.incrementAndGet();
        if (state.get() == State.HALF_OPEN && calls >= 10) {
            transitionTo(State.CLOSED);
        } else if (calls >= ringBufferSize) {
            evaluateSlidingWindow();
        }
    }

    private void recordFailureOrSlow() {
        failedOrSlowCalls.incrementAndGet();
        int calls = totalCalls.incrementAndGet();

        if (state.get() == State.HALF_OPEN) {
            transitionTo(State.OPEN);
        } else if (calls >= ringBufferSize) {
            evaluateSlidingWindow();
        }
    }

    private void evaluateSlidingWindow() {
        int total = totalCalls.get();
        int failed = failedOrSlowCalls.get();
        if (total >= ringBufferSize) {
            double rate = (double) failed / total;
            if (rate > failureRateThreshold) {
                transitionTo(State.OPEN);
            }
            resetCounters();
        }
    }

    private void transitionTo(State newState) {
        state.set(newState);
        lastStateTransitionNanos.set(System.nanoTime());
        resetCounters();
    }

    private void resetCounters() {
        totalCalls.set(0);
        failedOrSlowCalls.set(0);
    }

    public State getState() {
        return state.get();
    }

    public void tripForTest() {
        transitionTo(State.OPEN);
    }

    public void resetForTest() {
        transitionTo(State.CLOSED);
    }
}
