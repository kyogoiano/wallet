package br.com.wallet.core.exceptions;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class AccountBlockedException extends RuntimeException {

    private final UUID walletId;
    private final String reason;

    public AccountBlockedException(@Nullable UUID walletId, @Nullable String reason) {
        super("Account is blocked. walletId=" + walletId + (reason != null ? ", reason=" + reason : ""));
        this.walletId = walletId;
        this.reason = reason;
    }

    public AccountBlockedException(@Nullable String message) {
        super(message != null ? message : "Account is blocked");
        this.walletId = null;
        this.reason = message;
    }

    @Nullable
    public UUID getWalletId() {
        return walletId;
    }

    @Nullable
    public String getReason() {
        return reason;
    }
}
