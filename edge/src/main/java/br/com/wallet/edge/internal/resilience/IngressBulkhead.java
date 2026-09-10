package br.com.wallet.edge.internal.resilience;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded Ingress Bulkhead (REQ-EDG-005, I-EDGE-002).
 * Dynamically caps concurrent inflight command dispatches to edge.bulkhead.max-inflight (default 2048).
 */
public class IngressBulkhead {

    public static final int DEFAULT_MAX_INFLIGHT = 2048;

    private final int maxInflight;
    private final AtomicInteger currentInflight = new AtomicInteger(0);

    public IngressBulkhead() {
        this(DEFAULT_MAX_INFLIGHT);
    }

    public IngressBulkhead(int maxInflight) {
        if (maxInflight <= 0) {
            throw new IllegalArgumentException("maxInflight must be greater than 0");
        }
        this.maxInflight = maxInflight;
    }

    public boolean tryAcquire() {
        while (true) {
            int current = currentInflight.get();
            if (current >= maxInflight) {
                return false;
            }
            if (currentInflight.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void release() {
        currentInflight.updateAndGet(current -> Math.max(0, current - 1));
    }

    public int getCurrentInflight() {
        return currentInflight.get();
    }

    public int getMaxInflight() {
        return maxInflight;
    }
}
