package br.com.wallet.fraud.domain;

public sealed interface VelocityResult {
    record Ok(long count) implements VelocityResult {}
    record Exceeded(long count) implements VelocityResult {}
    record Replay(long count) implements VelocityResult {}
    record Unknown() implements VelocityResult {} // 👈 novo
}