package br.com.wallet.integration.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.api.model.RiskSubjectType;
import br.com.wallet.fraud.fusion.api.FusionEvaluationDispatcher;
import br.com.wallet.fraud.fusion.internal.gate.FraudGateV4;
import br.com.wallet.fraud.fusion.internal.gate.GateAuthorizationResult;
import br.com.wallet.fraud.fusion.internal.orchestration.FusionJobWorker;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJob;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobRepository;
import br.com.wallet.fraud.fusion.internal.persistence.FusionJobStatus;
import br.com.wallet.fraud.fusion.internal.persistence.RiskProfileStore;
import br.com.wallet.fraud.intelligence.domain.EntityType;
import br.com.wallet.fraud.intelligence.domain.FraudEntity;
import br.com.wallet.fraud.intelligence.internal.persistence.PostgresFraudRelationshipDao;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("FraudSignalFusionWorkflowIT: End-to-End Orchestration Convergence (TASK-6.1)")
public class FraudSignalFusionWorkflowIT extends DockerProperties {

    @Autowired
    private FusionEvaluationDispatcher dispatcher;

    @Autowired
    private FusionJobWorker worker;

    @Autowired
    private FusionJobRepository jobRepository;

    @Autowired
    private PostgresFraudRelationshipDao relationshipDao;

    @Autowired
    private RiskProfileStore profileStore;

    @Autowired
    private FraudGateV4 fraudGate;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("TASK-6.1: Full Pipeline — Dispatch -> Queue -> Worker -> ONNX -> Fusion -> Hot Cache -> Gate")
    void shouldExecuteEndToEndPipeline() {
        UUID entityId = UUID.randomUUID();

        // 1. Seed entity with elevated graph and behavioral risk
        FraudEntity entity = new FraudEntity(
            entityId,
            EntityType.USER,
            0.10, // direct
            0.85, // graph
            0.75, // behavioral
            0.60, // propagated
            0.0,
            Instant.now(),
            Instant.now(),
            null
        );
        relationshipDao.upsertEntity(entity);

        // 2. Event triggers evaluation dispatch into durable queue
        UUID jobId = dispatcher.dispatch(entityId, "v1", Instant.now(), "{\"trigger\":\"TRANSACTION_SUSPICIOUS\"}");
        assertThat(jobId).isNotNull();

        Optional<FusionJob> pendingJob = jobRepository.findById(jobId);
        assertThat(pendingJob).isPresent();
        assertThat(pendingJob.get().status()).isEqualTo(FusionJobStatus.PENDING);

        // 3. Worker executes the job
        boolean executed = worker.pollAndExecute();
        assertThat(executed).isTrue();

        // Verify job state transitioned to COMPLETED
        Optional<FusionJob> completedJob = jobRepository.findById(jobId);
        assertThat(completedJob).isPresent();
        assertThat(completedJob.get().status()).isEqualTo(FusionJobStatus.COMPLETED);

        // 4. Verify hot state materialized in Dragonfly/Redis
        RiskSubject subject = new RiskSubject(RiskSubjectType.USER, entityId.toString());
        Optional<RiskProfile> cachedProfileOpt = profileStore.getProfile(subject);
        assertThat(cachedProfileOpt).isPresent();

        RiskProfile cachedProfile = cachedProfileOpt.get();
        assertThat(cachedProfile.finalRisk()).isGreaterThanOrEqualTo(0.85);
        assertThat(cachedProfile.status()).isEqualTo(FraudDecision.RESTRICT);
        assertThat(cachedProfile.primaryDriver()).isNotEmpty();

        // 5. Fraud Gate V4 evaluates transaction using the newly cached profile
        GateAuthorizationResult gateResult = fraudGate.authorize(subject, new BigDecimal("250.00"));
        assertThat(gateResult.authorized()).isFalse();
        assertThat(gateResult.decision()).isEqualTo(FraudDecision.RESTRICT);
    }
}
