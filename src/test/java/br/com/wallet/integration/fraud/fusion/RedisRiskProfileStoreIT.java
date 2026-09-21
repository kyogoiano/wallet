package br.com.wallet.integration.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
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
        RiskSubject subject = new RiskSubject(RiskSubjectType.USER, UUID.randomUUID().toString(), "tenant-alpha");
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
    @DisplayName("REQ-FUSION-006: Key Format Verification — Verifies risk_profile:{tenantId}:{type}:{id} namespace")
    void shouldGenerateCorrectKeyFormat() {
        String id = UUID.randomUUID().toString();
        RiskSubject userSubject = new RiskSubject(RiskSubjectType.USER, id);
        assertThat(userSubject.toKey()).isEqualTo("risk_profile:default:USER:" + id);

        RiskSubject deviceSubject = new RiskSubject(RiskSubjectType.DEVICE, "dev-12345");
        assertThat(deviceSubject.toKey()).isEqualTo("risk_profile:default:DEVICE:dev-12345");

        RiskSubject tenantSubject = new RiskSubject(RiskSubjectType.USER, id, "tenant-corp");
        assertThat(tenantSubject.toKey()).isEqualTo("risk_profile:tenant-corp:USER:" + id);
    }

    @Test
    @DisplayName("REQ-SEC-018: Multi-Tenant Profile Isolation — Colliding entity IDs across tenants maintain separate profiles")
    void shouldIsolateProfilesAcrossTenantsOnIdCollision() {
        String entityId = UUID.randomUUID().toString();
        RiskSubject subjectA = new RiskSubject(RiskSubjectType.USER, entityId, "tenant-alpha");
        RiskSubject subjectB = new RiskSubject(RiskSubjectType.USER, entityId, "tenant-beta");

        RiskProfile profileA = new RiskProfile(
                1.0, 0.9, 0.8, 0.7, 0.85, 0.95,
                FraudDecision.HARD_BLOCK, "DIRECT_HARD_RULE", false, Instant.now()
        );
        RiskProfile profileB = new RiskProfile(
                0.05, 0.05, 0.05, 0.05, 0.05, 0.05,
                FraudDecision.ALLOW, "NONE", false, Instant.now()
        );

        store.putProfile(subjectA, profileA, Duration.ofMinutes(10));
        store.putProfile(subjectB, profileB, Duration.ofMinutes(10));

        Optional<RiskProfile> fetchedA = store.getProfile(subjectA);
        Optional<RiskProfile> fetchedB = store.getProfile(subjectB);

        assertThat(fetchedA).isPresent();
        assertThat(fetchedB).isPresent();

        assertThat(fetchedA.get().status()).isEqualTo(FraudDecision.HARD_BLOCK);
        assertThat(fetchedB.get().status()).isEqualTo(FraudDecision.ALLOW);

        store.evictProfile(subjectA);
        assertThat(store.getProfile(subjectA)).isEmpty();
        assertThat(store.getProfile(subjectB)).isPresent();
    }

    @Test
    @DisplayName("I-FUSION-006: Eviction — Successfully removes key from cache")
    void shouldEvictProfile() {
        RiskSubject subject = new RiskSubject(RiskSubjectType.ACCOUNT, UUID.randomUUID().toString(), "tenant-gamma");
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
