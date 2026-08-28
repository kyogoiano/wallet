package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.core.exceptions.IdempotencyException;
import br.com.wallet.ledger.api.exceptions.BusinessException;
import br.com.wallet.ledger.api.exceptions.FraudBlockedException;
import br.com.wallet.ledger.api.exceptions.PermanentException;
import br.com.wallet.ledger.api.exceptions.ReplayAttackException;

public class RetryPolicy {

    public static RetryDecision decide(final long deliveries, final Exception exception) {
        return switch (exception) {
            case IdempotencyException idempotencyException -> RetryDecision.ACK;
            case AccountBlockedException accountBlockedException -> RetryDecision.ACK;
            case FraudBlockedException fraudBlockedException -> RetryDecision.ACK;
            case ReplayAttackException replayAttackException -> RetryDecision.ACK;
            case PermanentException permanentException -> RetryDecision.ACK;
            case BusinessException businessException -> RetryDecision.ACK;
            case IllegalArgumentException illegalArgumentException -> RetryDecision.ACK;
            default -> {
                if (deliveries >= 5) {
                    yield RetryDecision.DLQ;
                }
                yield RetryDecision.RETRY;
            }
        };
    }
}
