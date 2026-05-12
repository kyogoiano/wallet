package br.com.wallet.fraud.infrasctructure;

import br.com.wallet.fraud.domain.SlidingWindow;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window implementation
 */
@Component
public class LocalStateStore {

    private final ConcurrentHashMap<UUID, SlidingWindow> store = new ConcurrentHashMap<>();


    public SlidingWindow getWindow(final UUID userId) {
        return store.computeIfAbsent(userId, k -> new SlidingWindow());
    }
}