package br.com.wallet.copilot;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.ApproveProposalCommand;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.dto.RejectProposalCommand;
import br.com.wallet.copilot.api.model.ProposalStatus;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.dao.ProposalDao;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTestBase.class)
@DisplayName("Proposal High-Concurrency Integration Tests (TASK-4.12, I-AI-005, I-AI-006)")
class ProposalConcurrentApprovalIT extends DockerProperties {

    @Autowired
    private ProposalUseCase proposalUseCase;

    @Autowired
    private ProposalDao proposalDao;

    @Autowired
    private AccountDao accountDao;

    @Autowired
    private DepositFundsUseCase depositFundsUseCase;

    @Autowired
    private DatabaseCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tenantId = "tenant-concurrent";
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

        // Seed initial funds
        depositFundsUseCase.handle(new Deposit(fromWalletId, userId, new BigDecimal("1000.00"), UUID.randomUUID(), tenantId));
    }

    @Test
    @DisplayName("I-AI-005 / I-AI-006: 100 concurrent approval attempts produce exactly ONE downstream transfer")
    void shouldHandle100ConcurrentApprovalsSafely() throws Exception {
        BigDecimal transferAmount = new BigDecimal("100.00");
        String params = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}", fromWalletId, toWalletId, transferAmount);

        ProposalResponse created = proposalUseCase.createProposal(
                tenantId,
                "agent-copilot",
                new CreateProposalCommand(fromWalletId, ProposalType.TRANSFER, params, "race-test-key")
        );

        int concurrency = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    ProposalResponse res = proposalUseCase.approveProposal(
                            tenantId,
                            "approver-" + index,
                            new ApproveProposalCommand(created.id())
                    );
                    if (res.status() == ProposalStatus.EXECUTED) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        FinancialProposal finalProposal = proposalDao.findById(created.id(), tenantId).orElseThrow();
        assertThat(finalProposal.status()).isEqualTo(ProposalStatus.EXECUTED);

        // Verify ledger entries: Exactly 1 DEBIT from fromWalletId, exactly 1 CREDIT to toWalletId (for this transfer)
        Integer debitCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE wallet_id = ? AND type = 'DEBIT'",
                Integer.class,
                fromWalletId
        );
        Integer creditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger WHERE wallet_id = ? AND type = 'CREDIT'",
                Integer.class,
                toWalletId
        );

        assertThat(debitCount).as("Ledger must have exactly 1 debit entry for fromWallet").isEqualTo(1);
        assertThat(creditCount).as("Ledger must have exactly 1 credit entry for toWallet").isEqualTo(1);

        // Verify balances
        BigDecimal fromBalance = accountDao.findAccount(fromWalletId).orElseThrow().balance();
        BigDecimal toBalance = accountDao.findAccount(toWalletId).orElseThrow().balance();

        assertThat(fromBalance).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(toBalance).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Competing transitions: Concurrent approval vs rejection resolves cleanly to single terminal state")
    void shouldResolveCompetingApprovalAndRejection() throws Exception {
        BigDecimal transferAmount = new BigDecimal("50.00");
        String params = String.format("{\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}", fromWalletId, toWalletId, transferAmount);

        ProposalResponse created = proposalUseCase.createProposal(
                tenantId,
                "agent-copilot",
                new CreateProposalCommand(fromWalletId, ProposalType.TRANSFER, params, "compete-test-key")
        );

        int threadsPerAction = 20;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadsPerAction * 2);

        for (int i = 0; i < threadsPerAction; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    proposalUseCase.approveProposal(tenantId, "approver", new ApproveProposalCommand(created.id()));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    proposalUseCase.rejectProposal(tenantId, "rejecter", new RejectProposalCommand(created.id(), "Manual reject"));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        FinancialProposal finalProposal = proposalDao.findById(created.id(), tenantId).orElseThrow();
        assertThat(finalProposal.status()).isIn(ProposalStatus.EXECUTED, ProposalStatus.REJECTED);

        if (finalProposal.status() == ProposalStatus.EXECUTED) {
            BigDecimal toBalance = accountDao.findAccount(toWalletId).orElseThrow().balance();
            assertThat(toBalance).isEqualByComparingTo(new BigDecimal("50.00"));
        } else {
            BigDecimal toBalance = accountDao.findAccount(toWalletId).orElseThrow().balance();
            assertThat(toBalance).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
