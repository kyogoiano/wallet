package br.com.wallet.exceptions;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public class ReplayAttackException extends RuntimeException {

    public ReplayAttackException(@NonNull UUID uuid) {
        super("Redis replay attack exception for: "+ uuid);
    }
}
