package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.exceptions.BusinessException;
import br.com.wallet.exceptions.PermanentException;

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
