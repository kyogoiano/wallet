package br.com.wallet.integration.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.fusion.internal.persistence.RedisRiskProfileStore;
import br.com.wallet.fraud.fusion.internal.persistence.RiskProfileStore;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("RedisRiskProfileStore Integration Tests (REQ-FUSION-006, I-FUSION-006)")
public class RedisRiskProfileStoreIT extends DockerProperties {

    @Autowired
    private RiskProfileStore store;

    @Test
    @DisplayName("REQ-FUSION-006: Materialize & Fetch — Stores typed subject hash and retrieves valid RiskProfile")
    void shouldMaterializeAndFetchProfile() {
        RiskSubject subject = new RiskSubject(RiskSubjectType.USER, UUID.randomUUID().toString());
        RiskProfile profile = new RiskProfile(
            0.10,
            0.75,
            0.50,
            0.85,
            0.65,
            0.88,
            FraudDecision.RESTRICT,
            "BEHAVIORAL_ANOMALY",
            false,
            Instant.now()
        );

        store.putProfile(subject, profile, Duration.ofHours(1));

        Optional<RiskProfile> fetchedOpt = store.getProfile(subject);
        assertThat(fetchedOpt).isPresent();

        RiskProfile fetched = fetchedOpt.get();
        assertThat(fetched.finalRisk()).isEqualTo(0.88);
        assertThat(fetched.status()).isEqualTo(FraudDecision.RESTRICT);
        assertThat(fetched.primaryDriver()).isEqualTo("BEHAVIORAL_ANOMALY");
        assertThat(fetched.degraded()).isFalse();
    }

    @Test
    @DisplayName("REQ-FUSION-006: Key Format Verification — Verifies risk_profile:{type}:{id} namespace")
    void shouldGenerateCorrectKeyFormat() {
        String id = UUID.randomUUID().toString();
        RiskSubject userSubject = new RiskSubject(RiskSubjectType.USER, id);
        assertThat(userSubject.toKey()).isEqualTo("risk_profile:USER:" + id);

        RiskSubject deviceSubject = new RiskSubject(RiskSubjectType.DEVICE, "dev-12345");
        assertThat(deviceSubject.toKey()).isEqualTo("risk_profile:DEVICE:dev-12345");
    }

    @Test
    @DisplayName("I-FUSION-006: Eviction — Successfully removes key from cache")
    void shouldEvictProfile() {
        RiskSubject subject = new RiskSubject(RiskSubjectType.ACCOUNT, UUID.randomUUID().toString());
        RiskProfile profile = new RiskProfile(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            FraudDecision.ALLOW, "NONE", false, Instant.now()
        );

        store.putProfile(subject, profile, Duration.ofMinutes(10));
        assertThat(store.getProfile(subject)).isPresent();

        store.evictProfile(subject);
        assertThat(store.getProfile(subject)).isEmpty();
    }
}
