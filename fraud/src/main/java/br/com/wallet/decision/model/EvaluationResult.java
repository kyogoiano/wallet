package br.com.wallet.decision.model;

import br.com.wallet.decision.evaluator.MismatchedDecisionTypeException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Encapsulates the complete result of an evaluation workload across multiple typed questions.
 *
 * @param evaluationId unique evaluation batch identifier
 * @param subjectId identifier of evaluated subject (e.g., account or user UUID)
 * @param questions list of evaluated questions
 * @param evaluatedAt timestamp when evaluation completed
 */
public record EvaluationResult(
    String evaluationId,
    String subjectId,
    List<EvaluatedQuestion<?>> questions,
    Instant evaluatedAt
) {

    public EvaluationResult {
        Objects.requireNonNull(evaluationId, "evaluationId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        questions = List.copyOf(questions != null ? questions : List.of());
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }

    /**
     * Retrieves the outcome for a specific question with compile-time type inference and runtime type-witness validation.
     *
     * @param <T> concrete decision value type
     * @param question question definition
     * @return typed outcome
     * @throws NoSuchElementException if question was not present in this evaluation
     * @throws MismatchedDecisionTypeException if runtime type witness does not match evaluated question
     */
    @SuppressWarnings("unchecked")
    public <T extends DecisionValue> DecisionOutcome<T> outcomeFor(final DecisionQuestion<T> question) {
        Objects.requireNonNull(question, "question must not be null");

        EvaluatedQuestion<?> match = questions.stream()
            .filter(eq -> eq.question().questionId().equals(question.questionId()))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Question not found: " + question.questionId()));

        if (!match.question().valueType().equals(question.valueType())) {
            throw new MismatchedDecisionTypeException(
                "Expected " + question.valueType().getSimpleName()
                    + " but found " + match.question().valueType().getSimpleName());
        }

        return (DecisionOutcome<T>) match.outcome();
    }
}
