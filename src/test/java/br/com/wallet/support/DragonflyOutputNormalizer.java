package br.com.wallet.support;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Normalization and equivalence assertion utility for Dragonfly 1.40 vs 2.0 dual-version oracle (REQ-DF20-013).
 * Ignores harmless hash field ordering, but strictly enforces mathematical equivalence and bounds residual
 * clock drift tolerance (Δ_clock <= 100ms).
 */
public final class DragonflyOutputNormalizer {

    public static final long MAX_CLOCK_DRIFT_MS = 100L;

    private DragonflyOutputNormalizer() {
    }

    /**
     * Canonicalizes key-value maps by sorting keys deterministically.
     */
    public static Map<String, String> normalizeMap(Map<String, String> input) {
        if (input == null) {
            return Collections.emptyMap();
        }
        return new TreeMap<>(input);
    }

    /**
     * Canonicalizes list output for deterministic comparison.
     */
    public static List<Object> normalizeList(List<?> input) {
        if (input == null) {
            return Collections.emptyList();
        }
        List<Object> normalized = new ArrayList<>(input.size());
        for (Object item : input) {
            if (item instanceof Map<?, ?> map) {
                Map<String, String> strMap = new HashMap<>();
                map.forEach((k, v) -> strMap.put(String.valueOf(k), String.valueOf(v)));
                normalized.add(normalizeMap(strMap));
            } else if (item instanceof List<?> list) {
                normalized.add(normalizeList(list));
            } else if (item instanceof Number num) {
                normalized.add(num.longValue());
            } else {
                normalized.add(String.valueOf(item));
            }
        }
        return normalized;
    }

    /**
     * Verifies that two TTL values match within the explicit clock drift tolerance boundary (Δ_clock <= 100ms).
     */
    public static void assertTtlWithinTolerance(long ttlMs1, long ttlMs2, long maxClockDriftMs) {
        long delta = Math.abs(ttlMs1 - ttlMs2);
        assertThat(delta)
                .withFailMessage("TTL drift of %d ms exceeds declared tolerance boundary of %d ms (ttl1=%d, ttl2=%d)",
                        delta, maxClockDriftMs, ttlMs1, ttlMs2)
                .isLessThanOrEqualTo(maxClockDriftMs);
    }

    public static void assertTtlWithinTolerance(long ttlMs1, long ttlMs2) {
        assertTtlWithinTolerance(ttlMs1, ttlMs2, MAX_CLOCK_DRIFT_MS);
    }

    /**
     * Asserts normalized semantic equivalence between outputs from Dragonfly 1.40 and Dragonfly 2.0:
     * Normalize(Result_DF1.40) == Normalize(Result_DF2.0)
     */
    @SuppressWarnings("unchecked")
    public static void assertNormalizedEquivalence(Object resultV1, Object resultV2) {
        if (resultV1 == null && resultV2 == null) {
            return;
        }
        if (resultV1 == null || resultV2 == null) {
            fail("Equivalence failure: one result is null (v1=%s, v2=%s)", resultV1, resultV2);
        }

        if (resultV1 instanceof Map<?, ?> map1 && resultV2 instanceof Map<?, ?> map2) {
            Map<String, String> norm1 = normalizeMap((Map<String, String>) map1);
            Map<String, String> norm2 = normalizeMap((Map<String, String>) map2);
            assertThat(norm2).isEqualTo(norm1);
        } else if (resultV1 instanceof List<?> list1 && resultV2 instanceof List<?> list2) {
            List<Object> norm1 = normalizeList(list1);
            List<Object> norm2 = normalizeList(list2);
            assertThat(norm2).isEqualTo(norm1);
        } else if (resultV1 instanceof Number num1 && resultV2 instanceof Number num2) {
            assertThat(num2.longValue()).isEqualTo(num1.longValue());
        } else {
            assertThat(String.valueOf(resultV2)).isEqualTo(String.valueOf(resultV1));
        }
    }
}
