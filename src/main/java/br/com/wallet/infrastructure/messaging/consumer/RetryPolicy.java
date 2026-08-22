package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.wallet.api.exceptions.BusinessException;
import br.com.wallet.wallet.api.exceptions.PermanentException;

public class RetryPolicy {

    public static RetryDecision decide(final long deliveries, final Exception exception) {
        return switch (exception) {
            case PermanentException permanentException -> RetryDecision.ACK;
            case BusinessException businessException ->  RetryDecision.ACK;
            default ->  {
                if (deliveries >= 5) {
                    yield RetryDecision.DLQ;
                }
                yield RetryDecision.RETRY;
            }
        };
    }
}
