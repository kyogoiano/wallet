package br.com.wallet.unit.fraud.embeddings;

import br.com.wallet.fraud.embeddings.internal.extraction.EntityTransactionalMetrics;
import br.com.wallet.fraud.embeddings.internal.extraction.FeatureVectorExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("FeatureVectorExtractor Unit Tests (16-D Feature Taxonomy across 7 Semantic Domains)")
class FeatureVectorExtractorTest {

    private FeatureVectorExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new FeatureVectorExtractor();
    }

    @Test
    @DisplayName("REQ-VEC-002: Should extract and compute all 16 bounded dimensions correctly")
    void shouldExtractAll16BoundedDimensions() {
        EntityTransactionalMetrics metrics = new EntityTransactionalMetrics(
            250,        // txCount30d -> d1 = 250/500 = 0.50
            25000.0,    // avgOutgoingAmount -> d2 = 25000/50000 = 0.50
            12500.0,    // amountStdDev -> d3 = 12500/25000 = 0.50
            15,         // maxVelocity1h -> d4 = 15/30 = 0.50
            100,        // nocturnalTxCount -> d5 = 100/250 = 0.40
            50,         // uniqueCounterparties -> d6 = 50/100 = 0.50
            25,         // newCounterparties7d -> d7 = 25/50 = 0.50
            10,         // rapidDrainCount -> d8 = 10/20 = 0.50
            75,         // distinctDestinations
            150,        // totalSentCount -> d9 = 75/150 = 0.50
            50,         // distinctSources
            100,        // totalReceivedCount -> d10 = 50/100 = 0.50
            5,          // internationalTxCount -> d11 = 5/10 = 0.50
            5,          // failedAuthCount -> d12 = 5/10 = 0.50
            2,          // deviceSwitchCount -> d13 = 2/5 = 0.40
            25,         // outOfPatternCount -> d14 = 25/250 = 0.10
            2,          // disputeCount -> d15 = 2/5 = 0.40
            100000.0    // totalOutgoingVolume -> d16 = 100000/200000 = 0.50
        );

        double[] features = extractor.extractRawFeatures(metrics);

        assertThat(features).hasSize(16);
        assertThat(features[0]).isCloseTo(0.50, within(0.0001)); // tx_frequency
        assertThat(features[1]).isCloseTo(0.50, within(0.0001)); // avg_amount
        assertThat(features[2]).isCloseTo(0.50, within(0.0001)); // amount_std_dev
        assertThat(features[3]).isCloseTo(0.50, within(0.0001)); // velocity_spike_1h
        assertThat(features[4]).isCloseTo(0.40, within(0.0001)); // nocturnal_ratio
        assertThat(features[5]).isCloseTo(0.50, within(0.0001)); // unique_counterparties
        assertThat(features[6]).isCloseTo(0.50, within(0.0001)); // new_counterparties_7d
        assertThat(features[7]).isCloseTo(0.50, within(0.0001)); // rapid_drain_count
        assertThat(features[8]).isCloseTo(0.50, within(0.0001)); // fan_out_ratio
        assertThat(features[9]).isCloseTo(0.50, within(0.0001)); // fan_in_ratio
        assertThat(features[10]).isCloseTo(0.50, within(0.0001)); // intl_tx_count
        assertThat(features[11]).isCloseTo(0.50, within(0.0001)); // failed_auth_count
        assertThat(features[12]).isCloseTo(0.40, within(0.0001)); // device_switch_count
        assertThat(features[13]).isCloseTo(0.10, within(0.0001)); // out_of_pattern_ratio
        assertThat(features[14]).isCloseTo(0.40, within(0.0001)); // dispute_count
        assertThat(features[15]).isCloseTo(0.50, within(0.0001)); // total_amount_volume
    }

    @Test
    @DisplayName("REQ-VEC-002: Should clamp all 16 dimensions to upper bound of 1.0 when exceeded")
    void shouldClampAllDimensionsToOne() {
        EntityTransactionalMetrics extremeMetrics = new EntityTransactionalMetrics(
            5000,       // txCount30d >> 500
            500000.0,   // avgOutgoingAmount >> 50000
            250000.0,   // amountStdDev >> 25000
            300,        // maxVelocity1h >> 30
            5000,       // nocturnalTxCount
            1000,       // uniqueCounterparties >> 100
            500,        // newCounterparties7d >> 50
            200,        // rapidDrainCount >> 20
            1000, 1000, // fanOut
            1000, 1000, // fanIn
            100,        // intl >> 10
            100,        // failedAuth >> 10
            50,         // deviceSwitch >> 5
            5000,       // outOfPattern
            50,         // dispute >> 5
            5000000.0   // volume >> 200000
        );

        double[] features = extractor.extractRawFeatures(extremeMetrics);

        for (int i = 0; i < 16; i++) {
            assertThat(features[i]).as("Dimension d%d must clamp to 1.0", i + 1).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("I-VEC-008: Should return all zeros when entity metrics have zero activity")
    void shouldReturnZerosWhenMetricsAreZero() {
        EntityTransactionalMetrics emptyMetrics = EntityTransactionalMetrics.empty();

        double[] features = extractor.extractRawFeatures(emptyMetrics);

        for (int i = 0; i < 16; i++) {
            assertThat(features[i]).as("Dimension d%d must be 0.0", i + 1).isEqualTo(0.0);
        }
    }
}
