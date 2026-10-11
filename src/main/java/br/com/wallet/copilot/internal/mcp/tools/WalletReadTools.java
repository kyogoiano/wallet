package br.com.wallet.copilot.internal.mcp.tools;

import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import br.com.wallet.goals.api.GoalStrategyUseCase;
import br.com.wallet.goals.api.dto.GoalStrategyResponse;
import br.com.wallet.intelligence.api.CashflowQueryUseCase;
import br.com.wallet.intelligence.api.SubscriptionQueryUseCase;
import br.com.wallet.intelligence.api.dto.CashflowProjectionResponse;
import br.com.wallet.intelligence.api.dto.SubscriptionResponse;
import br.com.wallet.intelligence.api.model.SubscriptionStatus;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.BalanceUseCase;
import br.com.wallet.ledger.api.domain.Account;
import br.com.wallet.ledger.api.exceptions.AccountNotFoundException;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-Only MCP Tool Catalog exposing financial inquiries to AI agents (REQ-MCP-002, I-MCP-001).
 * Asserts resource tenant ownership prior to invoking downstream domain use cases (I-AI-004).
 */
@Component
public class WalletReadTools {

    public record BalanceResult(@NonNull String walletId, @NonNull String balance, @NonNull String tenantId) {}

    private final AccountUseCase accountUseCase;
    private final BalanceUseCase balanceUseCase;
    private final CashflowQueryUseCase cashflowQueryUseCase;
    private final SubscriptionQueryUseCase subscriptionQueryUseCase;
    private final GoalStrategyUseCase goalStrategyUseCase;

    public WalletReadTools(
            @NonNull final AccountUseCase accountUseCase,
            @NonNull final BalanceUseCase balanceUseCase,
            @NonNull final CashflowQueryUseCase cashflowQueryUseCase,
            @NonNull final SubscriptionQueryUseCase subscriptionQueryUseCase,
            @NonNull final GoalStrategyUseCase goalStrategyUseCase
    ) {
        this.accountUseCase = Objects.requireNonNull(accountUseCase, "accountUseCase cannot be null");
        this.balanceUseCase = Objects.requireNonNull(balanceUseCase, "balanceUseCase cannot be null");
        this.cashflowQueryUseCase = Objects.requireNonNull(cashflowQueryUseCase, "cashflowQueryUseCase cannot be null");
        this.subscriptionQueryUseCase = Objects.requireNonNull(subscriptionQueryUseCase, "subscriptionQueryUseCase cannot be null");
        this.goalStrategyUseCase = Objects.requireNonNull(goalStrategyUseCase, "goalStrategyUseCase cannot be null");
    }

    @McpTool(name = "wallet_get_balance", description = "Query current balance projection for a wallet")
    @NonNull
    public BalanceResult getBalance(
            @McpToolParam(required = true, description = "UUID of the wallet") @NonNull final String walletId
    ) {
        UUID id = UUID.fromString(walletId);
        String tenantId = assertWalletOwnership(id);
        BigDecimal balance = balanceUseCase.getBalance(id);
        return new BalanceResult(walletId, balance.toPlainString(), tenantId);
    }

    @McpTool(name = "wallet_get_cashflow_projections", description = "Query forward cashflow projections and commitments for a wallet")
    @NonNull
    public CashflowProjectionResponse getCashflowProjections(
            @McpToolParam(required = true, description = "UUID of the wallet") @NonNull final String walletId
    ) {
        UUID id = UUID.fromString(walletId);
        String tenantId = assertWalletOwnership(id);
        return cashflowQueryUseCase.getProjections(tenantId, id);
    }

    @McpTool(name = "wallet_get_active_subscriptions", description = "Query active recurring subscriptions for a wallet")
    @NonNull
    public List<SubscriptionResponse> getActiveSubscriptions(
            @McpToolParam(required = true, description = "UUID of the wallet") @NonNull final String walletId
    ) {
        UUID id = UUID.fromString(walletId);
        String tenantId = assertWalletOwnership(id);
        return subscriptionQueryUseCase.getSubscriptions(tenantId, id, SubscriptionStatus.ACTIVE);
    }

    @McpTool(name = "wallet_simulate_goal_strategy", description = "Stateless simulation of a financial goal target without state mutation")
    @NonNull
    public GoalStrategyResponse simulateGoalStrategy(
            @McpToolParam(required = true, description = "UUID of the goal") @NonNull final String goalId,
            @McpToolParam(required = true, description = "Target date in ISO-8601 format (YYYY-MM-DD)") @NonNull final String targetDate
    ) {
        UUID id = UUID.fromString(goalId);
        LocalDate date = LocalDate.parse(targetDate);
        return goalStrategyUseCase.calculateStrategy(id, date);
    }

    @NonNull
    private String assertWalletOwnership(@NonNull final UUID walletId) {
        String tenantId = McpSecurityContext.getTenantId();
        Account account = accountUseCase.find(walletId);
        if (account == null || !tenantId.equals(account.tenantId())) {
            throw new AccountNotFoundException();
        }
        return tenantId;
    }
}
