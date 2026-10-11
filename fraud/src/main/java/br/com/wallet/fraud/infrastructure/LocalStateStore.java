package br.com.wallet.fraud.infrastructure;

import br.com.wallet.fraud.domain.SlidingAmountWindow;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Local state store for fraud detection, using Caffeine for efficient caching and eviction.
 */
@Component
public class LocalStateStore {

    // Define the window size in seconds, matching the rule's windowMs
    private static final int WINDOW_SIZE_SECONDS = 30; // 30 seconds for the sliding window

    private final Cache<UUID, SlidingAmountWindow> store =
            Caffeine.newBuilder()
                    .expireAfterAccess(Duration.ofMinutes(10)) // Cache entry expires if not accessed for 10 minutes
                    .maximumSize(1_000_000) // Max 1 million entries
                    .build();

    /**
     * Retrieves or creates a SlidingAmountWindow for a given user.
     * The window is automatically managed by Caffeine's eviction policies.
     * @param userId The ID of the user.
     * @return The SlidingAmountWindow for the user.
     */
    public SlidingAmountWindow getWindow(final UUID userId) {
        return store.get(userId, k -> new SlidingAmountWindow(WINDOW_SIZE_SECONDS));
    }
}
