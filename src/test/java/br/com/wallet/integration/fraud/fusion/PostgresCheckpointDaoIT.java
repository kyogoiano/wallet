package br.com.wallet.integration.fraud.fusion;

import br.com.wallet.fraud.fusion.api.model.CheckpointRecord;
import br.com.wallet.fraud.fusion.internal.persistence.PostgresCheckpointDao;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("PostgresCheckpointDao Integration Tests (REQ-FUSION-008, I-FUSION-007)")
public class PostgresCheckpointDaoIT extends DockerProperties {

    @Autowired
    private PostgresCheckpointDao dao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("REQ-FUSION-008: Checkpoint & Analyst Review Persistence — Stores checkpoint and overrides via review")
    void shouldStoreAndOverrideCheckpoints() {
        UUID entityId = UUID.randomUUID();
        String statePayload = "{\"reason\":\"SUSPICIOUS_VELOCITY\",\"primaryDriver\":\"GRAPH_INTELLIGENCE\"}";
        double finalRisk = 0.78;
        String classification = "SUSPICIOUS_REVIEW";

        // Step 1: Save checkpoint
        UUID checkpointId = dao.saveCheckpoint(entityId, statePayload, finalRisk, classification);
        assertThat(checkpointId).isNotNull();

        Optional<CheckpointRecord> checkpointOpt = dao.findCheckpointById(checkpointId);
        assertThat(checkpointOpt).isPresent();
        CheckpointRecord checkpoint = checkpointOpt.get();
        assertThat(checkpoint.entityId()).isEqualTo(entityId);
        assertThat(checkpoint.status()).isEqualTo("PENDING_ANALYST");
        assertThat(checkpoint.finalRisk()).isEqualTo(finalRisk);
        assertThat(checkpoint.riskClassification()).isEqualTo(classification);

        // Step 2: Analyst submits review override
        UUID reviewId = dao.saveReview(
            checkpointId,
            entityId,
            "analyst_42",
            "CONFIRMED_FRAUD",
            "Confirmed account takeover pattern via linked device"
        );
        assertThat(reviewId).isNotNull();

        // Step 3: Checkpoint status updated to ANALYST_REVIEWED
        Optional<CheckpointRecord> updatedOpt = dao.findCheckpointById(checkpointId);
        assertThat(updatedOpt).isPresent();
        assertThat(updatedOpt.get().status()).isEqualTo("ANALYST_REVIEWED");
    }
}
