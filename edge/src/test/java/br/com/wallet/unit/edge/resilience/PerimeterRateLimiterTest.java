package br.com.wallet.unit.edge.resilience;

import br.com.wallet.edge.api.RateLimitKey;
import br.com.wallet.edge.internal.resilience.PerimeterRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerimeterRateLimiter Tests (I-SEC-007, REQ-SEC-005, TASK-SEC-3.2)")
class PerimeterRateLimiterTest {

    @Test
    @DisplayName("I-SEC-007: Exhausting tenant A + principal A must return false while tenant B + principal B and tenant A + principal C remain undisturbed")
    void shouldIsolateRateLimitsBetweenTenantsAndPrincipals() {
        // 2 tokens capacity, 0 refill for deterministic test
        PerimeterRateLimiter limiter = new PerimeterRateLimiter(2, 0);

        RateLimitKey keyA1 = new RateLimitKey("tenant-A", "principal-A");
        RateLimitKey keyA2 = new RateLimitKey("tenant-A", "principal-C");
        RateLimitKey keyB1 = new RateLimitKey("tenant-B", "principal-B");

        // Consume all tokens for tenant A + principal A
        assertThat(limiter.tryAcquire(keyA1)).isTrue();
        assertThat(limiter.tryAcquire(keyA1)).isTrue();
        // Saturated:
        assertThat(limiter.tryAcquire(keyA1)).isFalse();

        // Tenant B + principal B must remain undisturbed
        assertThat(limiter.tryAcquire(keyB1)).isTrue();
        assertThat(limiter.tryAcquire(keyB1)).isTrue();

        // Same tenant A + different principal C must remain undisturbed (noisy neighbor protection)
        assertThat(limiter.tryAcquire(keyA2)).isTrue();
        assertThat(limiter.tryAcquire(keyA2)).isTrue();
    }

    @Test
    @DisplayName("REQ-SEC-005 & I-SEC-007: Must enforce bounded key cardinality and fail closed on overflow")
    void shouldEnforceBoundedCardinalityAndFailClosed() {
        // Max cardinality = 3
        PerimeterRateLimiter limiter = new PerimeterRateLimiter(10, 0, 3);

        RateLimitKey key1 = new RateLimitKey("tenant-1", "p-1");
        RateLimitKey key2 = new RateLimitKey("tenant-2", "p-2");
        RateLimitKey key3 = new RateLimitKey("tenant-3", "p-3");
        RateLimitKey overflowKey = new RateLimitKey("tenant-overflow", "p-overflow");

        assertThat(limiter.tryAcquire(key1)).isTrue();
        assertThat(limiter.tryAcquire(key2)).isTrue();
        assertThat(limiter.tryAcquire(key3)).isTrue();
        assertThat(limiter.getActiveKeyCount()).isEqualTo(3);

        // Overflow key must fail closed without allocating memory
        assertThat(limiter.tryAcquire(overflowKey)).isFalse();
        assertThat(limiter.getActiveKeyCount()).isEqualTo(3);

        // Pre-existing keys must continue to work within their quota
        assertThat(limiter.tryAcquire(key1)).isTrue();
    }

    @Test
    @DisplayName("Should maintain backward compatibility for string key calls")
    void shouldSupportStringKeyFallback() {
        PerimeterRateLimiter limiter = new PerimeterRateLimiter(5, 0);

        assertThat(limiter.tryAcquire("192.168.1.1")).isTrue();
        assertThat(limiter.tryAcquire("192.168.1.1", 2)).isTrue();
    }
}
