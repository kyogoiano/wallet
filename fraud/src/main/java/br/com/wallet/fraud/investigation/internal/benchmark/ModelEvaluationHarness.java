package br.com.wallet.fraud.investigation.internal.benchmark;

import br.com.wallet.fraud.investigation.api.model.InvestigationEvidence;
import br.com.wallet.fraud.investigation.api.model.InvestigationNarrative;
import br.com.wallet.fraud.investigation.internal.grounding.ClaimGroundingValidator;
import br.com.wallet.fraud.investigation.internal.sanitization.SanitizedInferenceContext;
import br.com.wallet.fraud.investigation.spi.InferenceBenchmarkThresholds;
import br.com.wallet.fraud.investigation.spi.InferenceCapability;
import br.com.wallet.fraud.investigation.spi.InferenceModelProfile;
import br.com.wallet.fraud.investigation.spi.LocalInferenceClient;
import br.com.wallet.fraud.investigation.spi.StructuredInferenceRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Hardware-aware evaluation harness measuring schema validity, claim grounding rate,
 * and P95 latency against gold-standard fixtures before model acceptance (REQ-VEC-007).
 */
@Component
public class ModelEvaluationHarness {

    private static final Logger log = LoggerFactory.getLogger(ModelEvaluationHarness.class);

    private final LocalInferenceClient inferenceClient;
    private final ClaimGroundingValidator groundingValidator;

    public ModelEvaluationHarness(
        @NonNull final LocalInferenceClient inferenceClient,
        @NonNull final ClaimGroundingValidator groundingValidator
    ) {
        this.inferenceClient = Objects.requireNonNull(inferenceClient, "inferenceClient cannot be null");
        this.groundingValidator = Objects.requireNonNull(groundingValidator, "groundingValidator cannot be null");
    }

    public record BenchmarkCase(
        @NonNull String caseId,
        @NonNull SanitizedInferenceContext context,
        @NonNull InvestigationEvidence rawEvidence
    ) {}

    public record BenchmarkReport(
        int totalCases,
        double jsonValidityRate,
        double groundingValidityRate,
        @NonNull Duration p95Latency,
        boolean passedGate
    ) {}

    @NonNull
    public BenchmarkReport evaluate(
        @NonNull final List<BenchmarkCase> cases,
        @NonNull final InferenceBenchmarkThresholds thresholds
    ) {
        Objects.requireNonNull(cases, "cases cannot be null");
        Objects.requireNonNull(thresholds, "thresholds cannot be null");

        if (cases.isEmpty()) {
            return new BenchmarkReport(0, 1.0, 1.0, Duration.ZERO, true);
        }

        int validJsonCount = 0;
        int validGroundingCount = 0;
        List<Long> latenciesMs = new ArrayList<>();

        for (BenchmarkCase testCase : cases) {
            StructuredInferenceRequest request = new StructuredInferenceRequest(
                testCase.context(),
                InferenceCapability.BALANCED,
                InferenceModelProfile.balanced(),
                "Benchmark System Prompt",
                "{}"
            );

            long start = System.nanoTime();
            Optional<InvestigationNarrative> result = inferenceClient.generateNarrative(request);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            latenciesMs.add(elapsedMs);

            if (result.isPresent()) {
                validJsonCount++;
                InvestigationNarrative narrative = result.get();
                if (groundingValidator.isValid(narrative, testCase.rawEvidence())) {
                    validGroundingCount++;
                }
            }
        }

        Collections.sort(latenciesMs);
        int p95Index = (int) Math.ceil(latenciesMs.size() * 0.95) - 1;
        p95Index = Math.max(0, Math.min(p95Index, latenciesMs.size() - 1));
        Duration p95Latency = Duration.ofMillis(latenciesMs.get(p95Index));

        double jsonValidityRate = (double) validJsonCount / cases.size();
        double groundingValidityRate = validJsonCount > 0 ? (double) validGroundingCount / validJsonCount : 0.0;

        boolean passedGate = jsonValidityRate >= thresholds.minJsonValidityRate()
            && groundingValidityRate >= thresholds.minGroundingValidityRate()
            && p95Latency.compareTo(thresholds.maxP95Latency()) <= 0;

        log.info("Benchmark gate completed: passed={}, jsonValidity={}, groundingValidity={}, p95Latency={}ms",
            passedGate, jsonValidityRate, groundingValidityRate, p95Latency.toMillis());

        return new BenchmarkReport(cases.size(), jsonValidityRate, groundingValidityRate, p95Latency, passedGate);
    }
}
