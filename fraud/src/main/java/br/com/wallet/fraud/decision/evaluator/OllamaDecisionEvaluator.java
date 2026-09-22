package br.com.wallet.fraud.decision.evaluator;

import br.com.wallet.fraud.decision.model.Confidence;
import br.com.wallet.fraud.decision.model.DecisionAnswer;
import br.com.wallet.fraud.decision.model.DecisionEvidence;
import br.com.wallet.fraud.decision.model.DecisionProvenance;
import br.com.wallet.fraud.decision.model.DecisionQuestion;
import br.com.wallet.fraud.decision.model.DecisionUnavailable;
import br.com.wallet.fraud.decision.model.DecisionValue;
import br.com.wallet.fraud.decision.model.EvaluatedQuestion;
import br.com.wallet.fraud.decision.model.EvaluationResult;
import br.com.wallet.fraud.decision.model.ScoreDecision;
import br.com.wallet.fraud.decision.model.UnavailableReason;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Provider-neutral decision evaluator implementation with timeout containment (I-TYPED-005).
 */
public class OllamaDecisionEvaluator implements DecisionEvaluator {

    @FunctionalInterface
    public interface InferenceBridge {
        CompletableFuture<Map<String, DecisionValue>> invoke(
            String subjectId,
            List<DecisionQuestion<?>> questions,
            Map<String, DecisionEvidence> evidence
        );
    }

    private final InferenceBridge inferenceBridge;
    private final Duration timeout;

    public OllamaDecisionEvaluator(final InferenceBridge inferenceBridge, final Duration timeout) {
        this.inferenceBridge = Objects.requireNonNull(inferenceBridge, "inferenceBridge must not be null");
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(5);
    }

    @Override
    public CompletableFuture<EvaluationResult> evaluate(
        final String subjectId,
        final List<DecisionQuestion<?>> questions,
        final Map<String, DecisionEvidence> evidence
    ) {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(questions, "questions must not be null");
        final long start = System.currentTimeMillis();
        final String evalId = UUID.randomUUID().toString();

        return inferenceBridge.invoke(subjectId, questions, evidence)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .handle((answersMap, ex) -> {
                long latency = System.currentTimeMillis() - start;
                DecisionProvenance prov = new DecisionProvenance("qwen2.5:7b-instruct", "v1.0", Instant.now(), latency);
                List<EvaluatedQuestion<?>> evaluatedList = new ArrayList<>();

                if (ex != null) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    UnavailableReason reason = cause instanceof TimeoutException
                        ? UnavailableReason.TIMEOUT
                        : UnavailableReason.PROVIDER_UNAVAILABLE;
                    String message = cause instanceof TimeoutException
                        ? "Evaluator execution timeout after " + timeout.toMillis() + "ms"
                        : "Evaluator invocation failed: " + cause.getMessage();

                    for (DecisionQuestion<?> q : questions) {
                        evaluatedList.add(createUnavailable(q, reason, message, prov));
                    }
                } else {
                    for (DecisionQuestion<?> q : questions) {
                        DecisionValue val = answersMap != null ? answersMap.get(q.questionId()) : null;
                        if (val != null) {
                            evaluatedList.add(createAnswer(q, val, evidence, prov));
                        } else {
                            evaluatedList.add(createUnavailable(
                                q, UnavailableReason.INSUFFICIENT_EVIDENCE, "No answer returned for question", prov
                            ));
                        }
                    }
                }

                return new EvaluationResult(evalId, subjectId, evaluatedList, Instant.now());
            });
    }

    private <T extends DecisionValue> EvaluatedQuestion<T> createAnswer(
        final DecisionQuestion<T> question,
        final DecisionValue val,
        final Map<String, DecisionEvidence> evidence,
        final DecisionProvenance provenance
    ) {
        T typedVal = question.valueType().cast(val);
        Confidence conf = (typedVal instanceof ScoreDecision s)
            ? Confidence.fromProbability(s.score())
            : Confidence.HIGH;

        List<DecisionEvidence> groundingList = evidence != null ? List.copyOf(evidence.values()) : List.of();
        DecisionAnswer<T> answer = new DecisionAnswer<>(typedVal, conf, groundingList, provenance);
        return new EvaluatedQuestion<>(question, answer);
    }

    private <T extends DecisionValue> EvaluatedQuestion<T> createUnavailable(
        final DecisionQuestion<T> question,
        final UnavailableReason reason,
        final String message,
        final DecisionProvenance provenance
    ) {
        DecisionUnavailable<T> unavailable = new DecisionUnavailable<>(reason, message, provenance);
        return new EvaluatedQuestion<>(question, unavailable);
    }
}
