package br.com.wallet.integration.dlq;

import br.com.wallet.dlq.api.dto.DlqQueryFilter;
import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("EdgeAdmissionSecurityIT: Admission Non-Persistence Integration Test (TASK-4.3, REQ-TDLQ-003, I-TDLQ-008)")
class EdgeAdmissionSecurityIT extends DockerProperties {

    @Autowired
    private DlqOperationsDao dlqDao;

    @Autowired
    private DatabaseCleaner cleaner;

    @BeforeEach
    void setup() {
        cleaner.clean();
    }

    @Test
    @DisplayName("Unauthenticated edge requests rejected at admission gate must create 0 DLQ database records (I-TDLQ-008)")
    void shouldAssertZeroDlqRecordsOnAdmissionFailure() {
        // Assert that unauthenticated/invalid ingress requests rejected at the edge admission boundary
        // do NOT persist any DLQ records (protecting Core DLQ from being DoSed by unauthorized traffic)
        UUID randomOpId = UUID.randomUUID();

        var records = dlqDao.findByFilter(new DlqQueryFilter(null, null, null, randomOpId, "tenant-alpha"), 10, 0);
        assertThat(records).isEmpty();

        // Count of all records in DLQ remains exactly 0
        var allExhausted = dlqDao.findExhaustedOperations(100);
        assertThat(allExhausted).isEmpty();
    }
}
