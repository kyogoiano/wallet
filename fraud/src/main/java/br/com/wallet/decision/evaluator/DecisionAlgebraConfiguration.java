package br.com.wallet.decision.evaluator;

import br.com.wallet.decision.model.BooleanDecision;
import br.com.wallet.decision.model.DecisionQuestion;
import br.com.wallet.decision.model.DecisionValue;
import br.com.wallet.decision.model.ScoreDecision;
import br.com.wallet.decision.model.TextDecision;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Spring configuration providing the default DecisionEvaluator SPI bean.
 */
@Configuration
public class DecisionAlgebraConfiguration {

    @Bean
    @ConditionalOnMissingBean(DecisionEvaluator.class)
    public DecisionEvaluator decisionEvaluator(
        @Value("${fraud.decision.evaluator.timeout:3s}") final Duration timeout
    ) {
        return new OllamaDecisionEvaluator(
            (subjectId, questions, evidence) -> CompletableFuture.supplyAsync(() -> {
                Map<String, DecisionValue> answers = new HashMap<>();
                for (final DecisionQuestion<?> q : questions) {
                    if (q.valueType().equals(BooleanDecision.class)) {
                        boolean hasAnomaly = evidence.values().stream()
                            .anyMatch(e -> e.facts().containsKey("finalRisk") &&
                                new BigDecimal(String.valueOf(e.facts().get("finalRisk")))
                                    .compareTo(new BigDecimal("0.70")) >= 0);
                        answers.put(q.questionId(), new BooleanDecision(hasAnomaly, "Evaluated against grounded evidence"));
                    } else if (q.valueType().equals(ScoreDecision.class)) {
                        BigDecimal score = evidence.values().stream()
                            .filter(e -> e.facts().containsKey("finalRisk"))
                            .map(e -> new BigDecimal(String.valueOf(e.facts().get("finalRisk"))))
                            .findFirst()
                            .orElse(new BigDecimal("0.50"));
                        answers.put(q.questionId(), new ScoreDecision(score, "Evaluated against grounded evidence"));
                    } else if (q.valueType().equals(TextDecision.class)) {
                        answers.put(q.questionId(), new TextDecision("Nearline semantic evaluation completed for subject " + subjectId));
                    }
                }
                return answers;
            }),
            timeout
        );
    }
}
