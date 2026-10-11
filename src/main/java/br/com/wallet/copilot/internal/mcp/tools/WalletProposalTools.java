package br.com.wallet.copilot.internal.mcp.tools;

import br.com.wallet.copilot.api.ProposalUseCase;
import br.com.wallet.copilot.api.dto.CreateProposalCommand;
import br.com.wallet.copilot.api.dto.ProposalResponse;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.mcp.config.McpSecurityContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Mutation Proposal MCP Tool Catalog exposing financial mutations requiring human-in-the-loop approval (REQ-MCP-003).
 * Enforces canonical scale-2 monetary representations in [0.01, 10^8] and persistent idempotency (I-MCP-006, I-AI-001).
 */
@Component
public class WalletProposalTools {

    private static final Pattern CANONICAL_AMOUNT_PATTERN = Pattern.compile("^[0-9]+\\.[0-9]{2}$");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000.00");

    private final ProposalUseCase proposalUseCase;
    private final ObjectMapper objectMapper;

    public WalletProposalTools(
            @NonNull final ProposalUseCase proposalUseCase,
            @NonNull final ObjectMapper objectMapper
    ) {
        this.proposalUseCase = Objects.requireNonNull(proposalUseCase, "proposalUseCase cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @McpTool(
            name = "copilot_propose_transfer",
            description = "Propose an asynchronous financial transfer requiring explicit human confirmation"
    )
    @NonNull
    public ProposalResponse proposeTransfer(
            @McpToolParam(required = true, description = "Source wallet UUID") @NonNull final String sourceWalletId,
            @McpToolParam(required = true, description = "Target wallet UUID") @NonNull final String targetWalletId,
            @McpToolParam(required = true, description = "Canonical scale-2 monetary amount (e.g. 150.00)") @NonNull final String amount,
            @McpToolParam(required = false, description = "Optional human-readable description") @Nullable final String description,
            @McpToolParam(required = true, description = "Mandatory client idempotency key") @NonNull final String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateCanonicalAmount(amount);

        UUID source = UUID.fromString(sourceWalletId);
        UUID target = UUID.fromString(targetWalletId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", source.toString());
        params.put("to", target.toString());
        params.put("amount", amount);
        if (description != null && !description.isBlank()) {
            params.put("description", description.trim());
        }

        String json = toJson(params);
        String tenantId = McpSecurityContext.getTenantId();
        String principal = McpSecurityContext.getPrincipal() != null ? McpSecurityContext.getPrincipal() : "mcp-agent";

        return proposalUseCase.createProposal(
                tenantId,
                principal,
                new CreateProposalCommand(source, ProposalType.TRANSFER, json, idempotencyKey.trim())
        );
    }

    @McpTool(
            name = "copilot_propose_savings_rule",
            description = "Propose adding a smart savings rule to an existing savings plan"
    )
    @NonNull
    public ProposalResponse proposeSavingsRule(
            @McpToolParam(required = true, description = "Savings plan UUID") @NonNull final String planId,
            @McpToolParam(required = true, description = "Rule type (PERCENTAGE, ROUND_UP, THRESHOLD)") @NonNull final String ruleType,
            @McpToolParam(required = false, description = "Percentage (e.g. 10.00)") @Nullable final String percentage,
            @McpToolParam(required = false, description = "Threshold amount") @Nullable final String threshold,
            @McpToolParam(required = false, description = "Step amount") @Nullable final String step,
            @McpToolParam(required = true, description = "Mandatory client idempotency key") @NonNull final String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        UUID planUuid = UUID.fromString(planId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("planId", planUuid.toString());
        params.put("ruleType", ruleType.trim().toUpperCase());
        if (percentage != null && !percentage.isBlank()) {
            validateCanonicalAmount(percentage);
            params.put("percentage", percentage);
        }
        if (threshold != null && !threshold.isBlank()) {
            validateCanonicalAmount(threshold);
            params.put("threshold", threshold);
        }
        if (step != null && !step.isBlank()) {
            validateCanonicalAmount(step);
            params.put("step", step);
        }

        String json = toJson(params);
        String tenantId = McpSecurityContext.getTenantId();
        String principal = McpSecurityContext.getPrincipal() != null ? McpSecurityContext.getPrincipal() : "mcp-agent";

        return proposalUseCase.createProposal(
                tenantId,
                principal,
                new CreateProposalCommand(planUuid, ProposalType.SAVINGS_RULE, json, idempotencyKey.trim())
        );
    }

    @McpTool(
            name = "copilot_propose_goal_adjustment",
            description = "Propose an adjustment to an existing financial goal target"
    )
    @NonNull
    public ProposalResponse proposeGoalAdjustment(
            @McpToolParam(required = true, description = "Financial goal UUID") @NonNull final String goalId,
            @McpToolParam(required = true, description = "Goal display name") @NonNull final String name,
            @McpToolParam(required = true, description = "Target monetary amount (scale 2)") @NonNull final String targetAmount,
            @McpToolParam(required = true, description = "Target date YYYY-MM-DD") @NonNull final String targetDate,
            @McpToolParam(required = true, description = "Priority (LOW, MEDIUM, HIGH)") @NonNull final String priority,
            @McpToolParam(required = true, description = "Mandatory client idempotency key") @NonNull final String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateCanonicalAmount(targetAmount);
        LocalDate date = LocalDate.parse(targetDate);
        if (!date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("targetDate must be a future date");
        }

        UUID goalUuid = UUID.fromString(goalId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("goalId", goalUuid.toString());
        params.put("name", name.trim());
        params.put("targetAmount", targetAmount);
        params.put("targetDate", targetDate);
        params.put("priority", priority.trim().toUpperCase());

        String json = toJson(params);
        String tenantId = McpSecurityContext.getTenantId();
        String principal = McpSecurityContext.getPrincipal() != null ? McpSecurityContext.getPrincipal() : "mcp-agent";

        return proposalUseCase.createProposal(
                tenantId,
                principal,
                new CreateProposalCommand(goalUuid, ProposalType.GOAL_ADJUSTMENT, json, idempotencyKey.trim())
        );
    }

    private void validateIdempotencyKey(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be null or blank");
        }
    }

    private void validateCanonicalAmount(final String amount) {
        if (amount == null || !CANONICAL_AMOUNT_PATTERN.matcher(amount).matches()) {
            throw new IllegalArgumentException("Amount must be a canonical scale-2 decimal string (^[0-9]+\\.[0-9]{2}$): " + amount);
        }
        BigDecimal parsed = new BigDecimal(amount);
        if (parsed.compareTo(MIN_AMOUNT) < 0 || parsed.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount out of allowable bounds [0.01, 100000000.00]: " + amount);
        }
    }

    private String toJson(final Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize parameters to JSON", e);
        }
    }
}
