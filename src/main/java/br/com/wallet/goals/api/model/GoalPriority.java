package br.com.wallet.goals.api.model;

public enum GoalPriority {
    CRITICAL(1),
    HIGH(2),
    MEDIUM(3),
    LOW(4);

    private final int rank;

    GoalPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
