package br.com.wallet.fraud.domain;

import java.util.ArrayDeque;
import java.util.Deque;

public class SlidingWindow {

    public final Deque<TransactionEntry> queue = new ArrayDeque<>();
    public long total = 0;

    public record TransactionEntry(long timestamp, long amount) {
    }
}