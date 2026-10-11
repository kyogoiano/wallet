package br.com.wallet.security.failure;

import br.com.wallet.security.replay.ReplayKey;

/**
 * Thrown when an ingress request replays a nonce that has already been committed or reserved (REQ-SEC-028, REQ-SEC-030).
 */
public class ReplayDetectedException extends RuntimeException {

    private final ReplayKey replayKey;

    public ReplayDetectedException(String message, ReplayKey replayKey) {
        super(message);
        this.replayKey = replayKey;
    }

    public ReplayKey getReplayKey() {
        return replayKey;
    }
}
