package br.com.wallet.fraud.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingAmountWindowTest {

    private static final int WINDOW_SIZE_SECONDS = 10;
    private SlidingAmountWindow window;

    @BeforeEach
    void setUp() {
        window = new SlidingAmountWindow(WINDOW_SIZE_SECONDS);
    }

    @Test
    @DisplayName("Should initialize with zero total amount")
    void shouldInitializeWithZeroTotalAmount() {
        assertThat(window.total()).isZero();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for non-positive window size")
    void shouldThrowExceptionForNonPositiveWindowSize() {
        assertThatThrownBy(() -> new SlidingAmountWindow(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Window size must be positive.");
        assertThatThrownBy(() -> new SlidingAmountWindow(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Window size must be positive.");
    }

    @Test
    @DisplayName("Should add amount within the same second")
    void shouldAddAmountWithinSameSecond() {
        long timestamp = System.currentTimeMillis();
        window.add(timestamp, 100);
        window.add(timestamp, 200);
        assertThat(window.total()).isEqualTo(300);
    }

    @Test
    @DisplayName("Should add amounts across different seconds within the window")
    void shouldAddAmountsAcrossDifferentSecondsWithinWindow() {
        long baseTimestamp = System.currentTimeMillis();
        window.add(baseTimestamp, 100);
        window.add(baseTimestamp + 1000, 200); // 1 second later
        window.add(baseTimestamp + 2000, 300); // 2 seconds later
        assertThat(window.total()).isEqualTo(600);
    }

    @Test
    @DisplayName("Should expire old amounts when window advances")
    void shouldExpireOldAmountsWhenWindowAdvances() throws InterruptedException {
        long baseTimestamp = System.currentTimeMillis();
        window.add(baseTimestamp, 100); // Bucket 0
        window.add(baseTimestamp + 1000, 200); // Bucket 1

        // Advance time past the window size to expire the first bucket

        window.add(baseTimestamp + WINDOW_SIZE_SECONDS * 1000, 50); // New transaction, should expire 100
        assertThat(window.total()).isEqualTo(250); // 200 + 50
    }

    @Test
    @DisplayName("Should expire all amounts when window advances significantly")
    void shouldExpireAllAmountsWhenWindowAdvancesSignificantly() throws InterruptedException {
        long baseTimestamp = System.currentTimeMillis();
        window.add(baseTimestamp, 100);
        window.add(baseTimestamp + 1000, 200);

        // Advance time significantly past the window
        window.add(baseTimestamp + (WINDOW_SIZE_SECONDS * 2) * 1000, 50); // New transaction
        assertThat(window.total()).isEqualTo(50); // Only the new transaction should remain
    }

    @Test
    @DisplayName("Should handle multiple transactions that fall into the same bucket after window advance")
    void shouldHandleMultipleTransactionsInSameBucketAfterAdvance() throws InterruptedException {
        long baseTimestamp = System.currentTimeMillis();
        window.add(baseTimestamp, 100); // Bucket X

        // These two should fall into the same new bucket (X+10 % 30 = X)
        window.add(baseTimestamp + (WINDOW_SIZE_SECONDS * 1000) + 500, 200);
        window.add(baseTimestamp + (WINDOW_SIZE_SECONDS * 1000) + 500, 300);

        assertThat(window.total()).isEqualTo(500); // Only the new transactions
    }

    @Test
    @DisplayName("Should accurately track total amount after mixed adds and advances")
    void shouldAccuratelyTrackTotalAmountAfterMixedOperations() throws InterruptedException {
        long baseTimestamp = System.currentTimeMillis(); // t0

        window.add(baseTimestamp, 100); // t0, bucket 0
        assertThat(window.total()).isEqualTo(100);

        window.add(baseTimestamp + 1000, 200); // t1, bucket 1
        assertThat(window.total()).isEqualTo(300);

        window.add(baseTimestamp + 2000, 300); // t2, bucket 2
        assertThat(window.total()).isEqualTo(600);

        // Advance 10 seconds (t0 expires, t1 and t2 remain)
        window.add(baseTimestamp + 10000, 400); // t10, bucket 0 (new cycle)
        assertThat(window.total()).isEqualTo(900); // 200 + 300 + 400

        // Advance 1 second (t1 expires, t2 and t10 remain)
        window.add(baseTimestamp + 11000, 500); // t11, bucket 1 (new cycle)
        assertThat(window.total()).isEqualTo(1200); // 300 + 400 + 500
    }
}
