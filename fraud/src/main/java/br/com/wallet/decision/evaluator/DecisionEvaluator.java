package br.com.wallet.decision.evaluator;

import br.com.wallet.decision.model.DecisionEvidence;
import br.com.wallet.decision.model.DecisionQuestion;
import br.com.wallet.decision.model.EvaluationResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provider-neutral Service Provider Interface (SPI) for asynchronous semantic decision evaluation.
 * Evaluators execute strictly nearline or asynchronously, air-gapped from the hot transaction path.
 */
public interface DecisionEvaluator {

    /**
     * Evaluates a set of typed questions against machine-verifiable evidence.
     *
     * @param subjectId identifier of evaluated entity (e.g. user or account ID)
     * @param questions list of typed questions to evaluate
     * @param evidence map of grounding evidence keyed by evidenceKey
     * @return CompletableFuture containing EvaluationResult, never completing exceptionally
     */
    CompletableFuture<EvaluationResult> evaluate(
        String subjectId,
        List<DecisionQuestion<?>> questions,
        Map<String, DecisionEvidence> evidence
    );
}
