package br.com.wallet.domain.context;

import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.domain.FraudCheckable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Withdraw implements TraceContext, FraudCheckable {
    private final @NonNull UUID walletId;
    private @Nullable UUID userId;
    private final @NonNull BigDecimal amount;
    private final @NonNull UUID operationId;

    public Withdraw(@NonNull UUID walletId,
                    @Nullable UUID userId,
                    @NonNull BigDecimal amount,
                    @NonNull UUID operationId) {
        this.walletId = walletId;
        this.userId = userId;
        this.amount = amount;
        this.operationId = operationId;
    }

    @Override
    public UUID operationId() {
        return this.operationId;
    }

    @Override
    public Map<String, String> traceTags() {
        return Map.of(
                "wallet.id", walletId.toString()
        );
    }

    @Override
    public UUID getSourceUserIdForFraudCheck() {
        return this.userId;
    }

    @Override
    public UUID getTargetUserIdForFraudCheck() {
        return null; // Withdrawals don't have a target user
    }

    public @NonNull UUID walletId() {
        return walletId;
    }

    @Override
    public @Nullable UUID userId() {
        return userId;
    }

    public void setUserId(@NonNull UUID userId) {
        this.userId = userId;
    }

    @Override
    public @NonNull BigDecimal amount() {
        return amount;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Withdraw) obj;
        return Objects.equals(this.walletId, that.walletId) &&
                Objects.equals(this.userId, that.userId) &&
                Objects.equals(this.amount, that.amount) &&
                Objects.equals(this.operationId, that.operationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(walletId, userId, amount, operationId);
    }

    @Override
    public String toString() {
        return "Withdraw[" +
                "walletId=" + walletId + ", " +
                "userId=" + userId + ", " +
                "amount=" + amount + ", " +
                "operationId=" + operationId + ']';
    }

}