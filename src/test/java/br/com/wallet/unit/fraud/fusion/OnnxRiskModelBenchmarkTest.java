package br.com.wallet.unit.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.MlFeatureVector;
import br.com.wallet.fraud.fusion.api.model.MlRiskResult;
import br.com.wallet.fraud.fusion.internal.ml.FraudFeatureMapper;
import br.com.wallet.fraud.fusion.internal.ml.OnnxModelLoader;
import br.com.wallet.fraud.fusion.internal.ml.OnnxRiskModelEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OnnxRiskModelBenchmark Performance Gate (I-FUSION-004: P95 < 2ms on CPU)")
@Tag("benchmark")
class OnnxRiskModelBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(OnnxRiskModelBenchmarkTest.class);

    private static OnnxModelLoader modelLoader;
    private static OnnxRiskModelEvaluator evaluator;

    @BeforeAll
    static void setUp() {
        modelLoader = new OnnxModelLoader();
        evaluator = new OnnxRiskModelEvaluator(modelLoader);
    }

    @AfterAll
    static void tearDown() {
        if (modelLoader != null) {
            modelLoader.close();
        }
    }

    @Test
    @DisplayName("I-FUSION-004: Embedded ONNX CPU Benchmark — Asserts P95 latency is strictly under 2.0ms")
    void shouldMeetP95LatencyGate() {
        if (!modelLoader.isAvailable()) {
            log.warn("Skipping benchmark as ONNX runtime native library or model is not loaded in current environment");
            return;
        }

        MlFeatureVector testVector = new MlFeatureVector(
            1,
            FraudFeatureMapper.CANONICAL_FEATURE_NAMES_V1,
            new float[]{0.15f, 0.70f, 0.45f, 0.30f, 250.0f, 5.0f}
        );

        // 1. Warmup JVM and JIT compiler
        for (int i = 0; i < 1000; i++) {
            evaluator.evaluate(testVector);
        }

        // 2. Measure timed iterations
        int iterations = 5000;
        double[] latenciesMs = new double[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            MlRiskResult result = evaluator.evaluate(testVector);
            long end = System.nanoTime();

            assertThat(result).isInstanceOf(MlRiskResult.Available.class);
            latenciesMs[i] = (end - start) / 1_000_000.0;
        }

        Arrays.sort(latenciesMs);

        double p50 = latenciesMs[(int) (iterations * 0.50)];
        double p95 = latenciesMs[(int) (iterations * 0.95)];
        double p99 = latenciesMs[(int) (iterations * 0.99)];

        log.info("ONNX Micro-ML Benchmark Results: P50 = {} ms, P95 = {} ms, P99 = {} ms",
            String.format("%.4f", p50),
            String.format("%.4f", p95),
            String.format("%.4f", p99)
        );

        // Assert performance gate: P95 < 2.0 ms
        assertThat(p95)
            .withFailMessage("P95 latency of %f ms exceeded the 2.0ms CPU budget!", p95)
            .isLessThan(2.0);
    }
}
