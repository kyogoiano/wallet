package br.com.wallet.copilot;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.copilot.internal.reconciliation.ProposalLeaseReconciler;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.internal.persistence.AccountDao;
import br.com.wallet.support.DatabaseCleaner;
import br.com.wallet.support.DockerProperties;
import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Proposal Crash Recovery Integration Tests (TASK-4.12, I-AI-001, I-AI-006, I-AI-010)")
class ProposalLeaseRecoveryIT extends DockerProperties {

    @Autowired
    private ProposalUseCase proposalUseCase;

    @Autowired
    private ProposalDao proposalDao;

    @Autowired
    private ProposalLeaseReconciler reconciler;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private TransferFundsUseCase transferFundsUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tenantId = "tenant-recovery";
    private UUID fromWalletId;
    private UUID toWalletId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        fromWalletId = UUID.randomUUID();
        toWalletId = UUID.randomUUID();
        userId = UUID.randomUUID();

        accountDao.insertAccount(fromWalletId, userId, tenantId);
        accountDao.insertAccount(toWalletId, userId, tenantId);

        depositFundsUseCase.handle(new Deposit(fromWalletId, userId, new BigDecimal("1000.00"), UUID.randomUUID(), tenantId));
    }

    @Test
    @DisplayName("Crash Recovery Gate A: Node crashed before downstream call -> reconciler completes execution safely")
    void shouldRecoverFromCrashBeforeDownstreamCall() {
        BigDecimal amount = new BigDecimal("150.00");
        String params = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}", fromWalletId, toWalletId, amount);

        ProposalResponse created = proposalUseCase.createProposal(
                tenantId,
                "agent-copilot",
                new CreateProposalCommand(fromWalletId, ProposalType.TRANSFER, params, "crash-before-key")
        );

        // Simulate crash: claimed by approver into EXECUTING, but node died before calling downstream
        Instant past = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant leaseExpired = past.plus(2, ChronoUnit.MINUTES);
        proposalDao.claimForExecution(created.id(), tenantId, "human-approver", past, leaseExpired);

        FinancialProposal crashedProposal = proposalDao.findById(created.id(), tenantId).orElseThrow();
        assertThat(crashedProposal.status()).isEqualTo(ProposalStatus.EXECUTING);
        assertThat(crashedProposal.isLeaseExpired(Instant.now())).isTrue();

        // Run reconciler
        FinancialProposal reconciled = reconciler.reconcileStaleProposal(crashedProposal);

        assertThat(reconciled.status()).isEqualTo(ProposalStatus.EXECUTED);
        assertThat(reconciled.executionReference()).isNotNull();

        // Downstream must have been executed exactly ONCE
        Integer debitCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE wallet_id = ? AND type = 'DEBIT'",
                Integer.class,
                fromWalletId
        );
        assertThat(debitCount).isEqualTo(1);

        BigDecimal fromBalance = accountDao.findAccount(fromWalletId).orElseThrow().balance();
        assertThat(fromBalance).isEqualByComparingTo(new BigDecimal("850.00"));
    }

    @Test
    @DisplayName("Crash Recovery Gate B: Node crashed after downstream call -> reconciler marks EXECUTED without repeating mutation")
    void shouldRecoverFromCrashAfterDownstreamCall() {
        BigDecimal amount = new BigDecimal("200.00");
        String params = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}", fromWalletId, toWalletId, amount);

        ProposalResponse created = proposalUseCase.createProposal(
                tenantId,
                "agent-copilot",
                new CreateProposalCommand(fromWalletId, ProposalType.TRANSFER, params, "crash-after-key")
        );

        // Simulate crash: claimed into EXECUTING
        Instant past = Instant.now().minus(5, ChronoUnit.MINUTES);
        Instant leaseExpired = past.plus(2, ChronoUnit.MINUTES);
        proposalDao.claimForExecution(created.id(), tenantId, "human-approver", past, leaseExpired);

        FinancialProposal executingProposal = proposalDao.findById(created.id(), tenantId).orElseThrow();

        // Downstream WAS executed successfully
        Transfer transfer = new Transfer(
                fromWalletId,
                toWalletId,
                amount,
                UUID.fromString(executingProposal.executionOperationId()),
                tenantId
        );
        transferFundsUseCase.handle(transfer);

        // But node crashed before updating copilot_proposals status to EXECUTED!
        // Run reconciler
        FinancialProposal reconciled = reconciler.reconcileStaleProposal(executingProposal);

        assertThat(reconciled.status()).isEqualTo(ProposalStatus.EXECUTED);

        // Crucial invariant check: Ledger entries must NOT be duplicated!
        Integer debitCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE wallet_id = ? AND type = 'DEBIT'",
                Integer.class,
                fromWalletId
        );
        assertThat(debitCount).as("Ledger must have only 1 debit entry, zero duplicate mutations").isEqualTo(1);

        BigDecimal fromBalance = accountDao.findAccount(fromWalletId).orElseThrow().balance();
        assertThat(fromBalance).isEqualByComparingTo(new BigDecimal("800.00"));
    }

    @Test
    @DisplayName("I-AI-010: Reconcile all stale leases processes multiple crashed proposals")
    void shouldReconcileMultipleStaleLeases() {
        BigDecimal amount = new BigDecimal("50.00");
        for (int i = 0; i < 5; i++) {
            String params = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}", fromWalletId, toWalletId, amount);
            ProposalResponse created = proposalUseCase.createProposal(
                    tenantId,
                    "agent-copilot",
                    new CreateProposalCommand(fromWalletId, ProposalType.TRANSFER, params, "multi-crash-key-" + i)
            );
            Instant past = Instant.now().minus(5, ChronoUnit.MINUTES);
            proposalDao.claimForExecution(created.id(), tenantId, "human-approver", past, past.plus(2, ChronoUnit.MINUTES));
        }

        reconciler.reconcileAllStaleLeases();

        // All 5 must now be EXECUTED
        List<FinancialProposal> staleRemaining = proposalDao.findStaleExecutingLeases(Instant.now());
        assertThat(staleRemaining).isEmpty();

        Integer debitCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE wallet_id = ? AND type = 'DEBIT'",
                Integer.class,
                fromWalletId
        );
        assertThat(debitCount).isEqualTo(5);

        BigDecimal fromBalance = accountDao.findAccount(fromWalletId).orElseThrow().balance();
        assertThat(fromBalance).isEqualByComparingTo(new BigDecimal("750.00"));
    }
}
