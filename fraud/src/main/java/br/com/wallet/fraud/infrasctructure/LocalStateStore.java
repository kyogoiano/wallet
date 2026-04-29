package br.com.wallet.fraud.infrasctructure;

import br.com.wallet.fraud.domain.SlidingWindow;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window implementation
 */
public class LocalStateStore {

    private final ConcurrentHashMap<String, SlidingWindow> store = new ConcurrentHashMap<>();


    public SlidingWindow getWindow(final String userId) {
        return store.computeIfAbsent(userId, k -> new SlidingWindow());
    }
}