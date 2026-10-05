package br.com.wallet.unit.dlq;

import br.com.wallet.dlq.internal.engine.FullJitterBackoffCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.function.LongFunction;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FullJitterBackoffCalculator Unit Tests (REQ-TDLQ-005, I-TDLQ-003)")
class FullJitterBackoffCalculatorTest {

    @ParameterizedTest(name = "retryCount={0} should have ceiling={1}s")
    @CsvSource({
            "0, 2",
            "1, 4",
            "2, 8",
            "3, 16",
            "4, 32",
            "5, 60",
            "6, 60",
            "10, 60"
    })
    @DisplayName("Should cap exponential ceiling at 60 seconds")
    void shouldCapExponentialCeilingAt60Seconds(int retryCount, long expectedCeilingSeconds) {
        long ceiling = FullJitterBackoffCalculator.calculateCeilingSeconds(retryCount);
        assertThat(ceiling).isEqualTo(expectedCeilingSeconds);
    }

    @Test
    @DisplayName("Should return maximum delay when random generates upper bound")
    void shouldReturnMaxDelayWhenRandomPicksUpper() {
        // RandomGenerator that always returns bound - 1 (inclusive max)
        RandomGenerator maxRandom = new StubRandomGenerator(bound -> bound - 1);

        Duration delay0 = FullJitterBackoffCalculator.calculateDelay(0, maxRandom);
        assertThat(delay0).isEqualTo(Duration.ofSeconds(2));

        Duration delay1 = FullJitterBackoffCalculator.calculateDelay(1, maxRandom);
        assertThat(delay1).isEqualTo(Duration.ofSeconds(4));

        Duration delay2 = FullJitterBackoffCalculator.calculateDelay(2, maxRandom);
        assertThat(delay2).isEqualTo(Duration.ofSeconds(8));

        Duration delay3 = FullJitterBackoffCalculator.calculateDelay(3, maxRandom);
        assertThat(delay3).isEqualTo(Duration.ofSeconds(16));

        Duration delayMax = FullJitterBackoffCalculator.calculateDelay(10, maxRandom);
        assertThat(delayMax).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("Should return zero delay when random generates origin 0")
    void shouldReturnZeroDelayWhenRandomPicksOrigin() {
        RandomGenerator minRandom = new StubRandomGenerator(bound -> 0L);

        Duration delay = FullJitterBackoffCalculator.calculateDelay(3, minRandom);
        assertThat(delay).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Should always produce delays within [0, ceiling] over multiple iterations")
    void shouldStayWithinBounds() {
        for (int retry = 0; retry <= 5; retry++) {
            long ceiling = FullJitterBackoffCalculator.calculateCeilingSeconds(retry);
            for (int i = 0; i < 50; i++) {
                Duration delay = FullJitterBackoffCalculator.calculateDelay(retry);
                assertThat(delay.toSeconds())
                        .isGreaterThanOrEqualTo(0L)
                        .isLessThanOrEqualTo(ceiling);
            }
        }
    }

    private record StubRandomGenerator(LongFunction<Long> generator) implements RandomGenerator {

        @Override
            public long nextLong(long origin, long bound) {
                return origin + generator.apply(bound - origin);
            }

            @Override
            public long nextLong() {
                return 0;
            }
        }
}
