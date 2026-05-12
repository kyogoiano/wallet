package br.com.wallet.fraud.domain;

public record VelocityResult(
        Status status,
        long count
) {

    public enum Status {
        OK,
        EXCEEDED,
        REPLAY
    }

    public static VelocityResult ok(long count) {
        return new VelocityResult(Status.OK, count);
    }

    public static VelocityResult exceeded(long count) {
        return new VelocityResult(Status.EXCEEDED, count);
    }

    public static VelocityResult replay(long count) {
        return new VelocityResult(Status.REPLAY, count);
    }
}
