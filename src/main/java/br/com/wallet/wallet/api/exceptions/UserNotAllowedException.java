package br.com.wallet.wallet.api.exceptions;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class UserNotAllowedException extends BusinessException {
    public UserNotAllowedException(@NonNull UUID commandUserId, @NonNull UUID accountUserId) {
        super("The user of the originated command: " + commandUserId + ", was not the same of the stored account: " + accountUserId);
    }
}
