package br.com.wallet.copilot.internal.mcp.tools;

import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import br.com.wallet.goals.api.GoalStrategyUseCase;
import br.com.wallet.goals.api.dto.GoalStrategyResponse;
import br.com.wallet.goals.api.model.GoalFeasibility;
import br.com.wallet.intelligence.api.CashflowQueryUseCase;
import br.com.wallet.intelligence.api.SubscriptionQueryUseCase;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.SubscriptionResponse;
import br.com.wallet.intelligence.api.model.Cadence;
import br.com.wallet.intelligence.api.model.CashflowStatus;
import br.com.wallet.intelligence.api.model.PriceState;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.intelligence.api.model.VarianceType;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.domain.AccountStatus;
import br.com.wallet.ledger.api.exceptions.AccountNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletReadToolsTest {

    @Mock
    private AccountUseCase accountUseCase;
    @Mock
    private BalanceUseCase balanceUseCase;
    @Mock
    private CashflowQueryUseCase cashflowQueryUseCase;
    @Mock
    private SubscriptionQueryUseCase subscriptionQueryUseCase;
    @Mock
    private GoalStrategyUseCase goalStrategyUseCase;

    private WalletReadTools tools;

    private final String tenantId = "tenant-alpha";
    private final String agentPrincipal = "agent-read-1";
    private final UUID walletId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        McpSecurityContext.setContext(tenantId, agentPrincipal);
        tools = new WalletReadTools(
                accountUseCase,
                balanceUseCase,
                cashflowQueryUseCase,
                subscriptionQueryUseCase,
                goalStrategyUseCase
        );
    }

    @AfterEach
    void tearDown() {
        McpSecurityContext.clear();
    }

    @Test
    @DisplayName("Positive: getBalance returns balance for verified owner wallet")
    void testGetBalancePositive() {
        Account account = new Account(
                walletId, BigDecimal.ZERO, 1L, userId, AccountStatus.ACTIVE,
                null, null, Instant.now(), tenantId
        );
        when(accountUseCase.find(walletId)).thenReturn(account);
        when(balanceUseCase.getBalance(walletId)).thenReturn(new BigDecimal("1500.50"));

        WalletReadTools.BalanceResult result = tools.getBalance(walletId.toString());

        assertThat(result.walletId()).isEqualTo(walletId.toString());
        assertThat(result.balance()).isEqualTo("1500.50");
        assertThat(result.tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Boundary: Cross-tenant wallet throws AccountNotFoundException (I-AI-004)")
    void testCrossTenantWalletThrowsNotFound() {
        Account crossTenantAccount = new Account(
                walletId, BigDecimal.ZERO, 1L, userId, AccountStatus.ACTIVE,
                null, null, Instant.now(), "other-tenant"
        );
        when(accountUseCase.find(walletId)).thenReturn(crossTenantAccount);

        assertThatThrownBy(() -> tools.getBalance(walletId.toString()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Positive: getCashflowProjections returns projections for owner wallet")
    void testGetCashflowProjectionsPositive() {
        Account account = new Account(
                walletId, BigDecimal.ZERO, 1L, userId, AccountStatus.ACTIVE,
                null, null, Instant.now(), tenantId
        );
        when(accountUseCase.find(walletId)).thenReturn(account);

        CashflowProjectionResponse projection = new CashflowProjectionResponse(
                tenantId, walletId, new BigDecimal("1000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, CashflowStatus.SURPLUS,
                BigDecimal.ZERO, 0
        );
        when(cashflowQueryUseCase.getProjections(tenantId, walletId)).thenReturn(projection);

        CashflowProjectionResponse result = tools.getCashflowProjections(walletId.toString());

        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.currentBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Positive: getActiveSubscriptions returns subscriptions for owner wallet")
    void testGetActiveSubscriptionsPositive() {
        Account account = new Account(
                walletId, BigDecimal.ZERO, 1L, userId, AccountStatus.ACTIVE,
                null, null, Instant.now(), tenantId
        );
        when(accountUseCase.find(walletId)).thenReturn(account);

        SubscriptionResponse sub = new SubscriptionResponse(
                UUID.randomUUID(), tenantId, walletId, UUID.randomUUID(),
                Cadence.MONTHLY, SubscriptionStatus.ACTIVE, PriceState.NORMAL,
                "STREAMING", new BigDecimal("39.90"), new BigDecimal("39.90"),
                new BigDecimal("0.90"), 3, VarianceType.FIXED,
                Instant.now(), Instant.now()
        );
        when(subscriptionQueryUseCase.getSubscriptions(tenantId, walletId, SubscriptionStatus.ACTIVE))
                .thenReturn(List.of(sub));

        List<SubscriptionResponse> results = tools.getActiveSubscriptions(walletId.toString());

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().averageAmount()).isEqualByComparingTo(new BigDecimal("39.90"));
    }

    @Test
    @DisplayName("Positive: simulateGoalStrategy performs stateless simulation without mutation")
    void testSimulateGoalStrategyPositive() {
        UUID goalId = UUID.randomUUID();
        LocalDate targetDate = LocalDate.now().plusMonths(6);

        GoalStrategyResponse response = new GoalStrategyResponse(
                goalId,
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                6L,
                new BigDecimal("666.67"),
                new BigDecimal("1000.00"),
                new BigDecimal("666.67"),
                GoalFeasibility.ON_TRACK,
                targetDate,
                LocalDate.now()
        );
        when(goalStrategyUseCase.calculateStrategy(goalId, targetDate)).thenReturn(response);

        GoalStrategyResponse result = tools.simulateGoalStrategy(goalId.toString(), targetDate.toString());

        assertThat(result.goalId()).isEqualTo(goalId);
        assertThat(result.feasibility()).isEqualTo(GoalFeasibility.ON_TRACK);
    }
}
