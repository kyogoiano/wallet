package br.com.wallet.fraud.decision.evaluator;

/**
 * Thrown when runtime type witness validation fails between a question and its evaluated outcome.
 */
public class MismatchedDecisionTypeException extends RuntimeException {

    public MismatchedDecisionTypeException(final String message) {
        super(message);
    }

    public MismatchedDecisionTypeException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
