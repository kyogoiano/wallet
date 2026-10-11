package br.com.wallet.copilot;

import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("ProposalDao Integration Tests (TASK-4.5, REQ-COPILOT-002, REQ-COPILOT-005, I-AI-005, I-AI-007, I-AI-008)")
class ProposalDaoIT extends DockerProperties {

    @Autowired
    private ProposalDao proposalDao;

    @Autowired
    private DatabaseCleaner cleaner;

    private final String tenantId = "tenant-it";
    private final UUID walletId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    private FinancialProposal buildProposal(UUID id, String idempotencyKey, String opId, ProposalStatus status, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String params = "{\"amount\":250.00,\"to\":\"" + UUID.randomUUID() + "\"}";
        return new FinancialProposal(
                id,
                tenantId,
                walletId,
                ProposalType.TRANSFER,
                params,
                br.com.wallet.copilot.internal.util.ParametersHashUtil.computeHash(params),
                status,
                idempotencyKey,
                opId,
                "operator-1",
                null,
                now,
                expiresAt != null ? expiresAt : now.plus(15, ChronoUnit.MINUTES),
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("Should insert and find proposal by ID and tenant")
    void shouldInsertAndFindProposalById() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "key-1", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);

        boolean inserted = proposalDao.insert(proposal);
        assertThat(inserted).isTrue();

        Optional<FinancialProposal> found = proposalDao.findById(id, tenantId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(id);
        assertThat(found.get().parametersHash()).isEqualTo(proposal.parametersHash());
        assertThat(found.get().status()).isEqualTo(ProposalStatus.PROPOSED);
    }

    @Test
    @DisplayName("I-AI-008: Should enforce creation idempotency on (tenant_id, idempotency_key)")
    void shouldEnforceCreationIdempotency() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        String key = "shared-key";
        FinancialProposal proposal1 = buildProposal(id1, key, UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);
        FinancialProposal proposal2 = buildProposal(id2, key, UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);

        boolean inserted1 = proposalDao.insert(proposal1);
        boolean inserted2 = proposalDao.insert(proposal2);

        assertThat(inserted1).isTrue();
        assertThat(inserted2).isFalse();

        Optional<FinancialProposal> byKey = proposalDao.findByTenantAndIdempotencyKey(tenantId, key);
        assertThat(byKey).isPresent();
        assertThat(byKey.get().id()).isEqualTo(id1);
    }

    @Test
    @DisplayName("I-AI-006: Should enforce unique execution_operation_id constraint")
    void shouldEnforceUniqueExecutionOperationId() {
        String sharedOpId = UUID.randomUUID().toString();
        FinancialProposal p1 = buildProposal(UUID.randomUUID(), "key-a", sharedOpId, ProposalStatus.PROPOSED, null);
        FinancialProposal p2 = buildProposal(UUID.randomUUID(), "key-b", sharedOpId, ProposalStatus.PROPOSED, null);

        assertThat(proposalDao.insert(p1)).isTrue();
        assertThatThrownBy(() -> proposalDao.insert(p2))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("I-AI-005: Should claim proposal for execution atomically")
    void shouldClaimProposalForExecutionAtomically() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "key-claim", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);
        proposalDao.insert(proposal);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant leaseUntil = now.plus(2, ChronoUnit.MINUTES);

        boolean claimed = proposalDao.claimForExecution(id, tenantId, "human-approver", now, leaseUntil);
        assertThat(claimed).isTrue();

        // Second claim attempt must fail
        boolean secondClaim = proposalDao.claimForExecution(id, tenantId, "other-approver", now, leaseUntil);
        assertThat(secondClaim).isFalse();

        FinancialProposal updated = proposalDao.findById(id, tenantId).orElseThrow();
        assertThat(updated.status()).isEqualTo(ProposalStatus.EXECUTING);
        assertThat(updated.approvedBy()).isEqualTo("human-approver");
        assertThat(updated.executionLeaseUntil()).isNotNull();
    }

    @Test
    @DisplayName("REQ-COPILOT-006: Should reject claim when proposal has expired")
    void shouldRejectClaimWhenExpired() {
        UUID id = UUID.randomUUID();
        Instant expiredAt = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        FinancialProposal proposal = buildProposal(id, "key-exp", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, expiredAt);
        proposalDao.insert(proposal);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        boolean claimed = proposalDao.claimForExecution(id, tenantId, "approver", now, now.plus(2, ChronoUnit.MINUTES));
        assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("Should mark proposal EXECUTED with execution reference")
    void shouldMarkExecuted() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "key-exec", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);
        proposalDao.insert(proposal);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        proposalDao.claimForExecution(id, tenantId, "approver", now, now.plus(2, ChronoUnit.MINUTES));

        boolean marked = proposalDao.markExecuted(id, tenantId, now.plus(1, ChronoUnit.SECONDS), "tx-ref-12345");
        assertThat(marked).isTrue();

        FinancialProposal executed = proposalDao.findById(id, tenantId).orElseThrow();
        assertThat(executed.status()).isEqualTo(ProposalStatus.EXECUTED);
        assertThat(executed.executionReference()).isEqualTo("tx-ref-12345");
    }

    @Test
    @DisplayName("Should mark proposal REJECTED only from PROPOSED")
    void shouldMarkRejectedOnlyFromProposed() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "key-rej", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);
        proposalDao.insert(proposal);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        boolean rejected = proposalDao.markRejected(id, tenantId, "human-reviewer", now);
        assertThat(rejected).isTrue();

        FinancialProposal found = proposalDao.findById(id, tenantId).orElseThrow();
        assertThat(found.status()).isEqualTo(ProposalStatus.REJECTED);

        // Cannot claim once rejected
        boolean claimed = proposalDao.claimForExecution(id, tenantId, "approver", now, now.plus(2, ChronoUnit.MINUTES));
        assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("Should find stale executing leases for crash recovery")
    void shouldFindStaleExecutingLeases() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "key-stale", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);
        proposalDao.insert(proposal);

        Instant past = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        proposalDao.claimForExecution(id, tenantId, "approver", past, past.plus(2, ChronoUnit.MINUTES));

        List<FinancialProposal> stale = proposalDao.findStaleExecutingLeases(Instant.now());
        assertThat(stale).extracting(FinancialProposal::id).contains(id);
    }

    @Test
    @DisplayName("I-AI-004: Should enforce cross-tenant isolation (404 / Optional.empty)")
    void shouldEnforceCrossTenantIsolation() {
        UUID id = UUID.randomUUID();
        FinancialProposal proposal = buildProposal(id, "key-cross", UUID.randomUUID().toString(), ProposalStatus.PROPOSED, null);
        proposalDao.insert(proposal);

        Optional<FinancialProposal> foundWrongTenant = proposalDao.findById(id, "tenant-other");
        assertThat(foundWrongTenant).isEmpty();
    }
}
