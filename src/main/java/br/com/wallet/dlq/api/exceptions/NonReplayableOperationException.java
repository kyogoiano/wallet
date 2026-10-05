package br.com.wallet.dlq.api.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operator attempts to replay an operation whose ciphertext
 * has suffered cryptographic tampering (AEAD tag mismatch) (REQ-TDLQ-009, I-TDLQ-007).
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class NonReplayableOperationException extends RuntimeException {

    public NonReplayableOperationException(final String message) {
        super(message);
    }
}
