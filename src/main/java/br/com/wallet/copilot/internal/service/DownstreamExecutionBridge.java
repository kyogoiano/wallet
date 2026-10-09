package br.com.wallet.copilot.internal.service;

import br.com.wallet.core.context.OperationOrigin;
import br.com.wallet.core.exceptions.AccountBlockedException;
import br.com.wallet.copilot.api.model.ProposalType;
import br.com.wallet.copilot.internal.model.FinancialProposal;
import br.com.wallet.goals.api.FinancialGoalUseCase;
import br.com.wallet.goals.api.dto.UpdateGoalCommand;
import br.com.wallet.goals.api.model.GoalPriority;
import br.com.wallet.ledger.api.OperationQueryUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.domain.OperationStatus;
import br.com.wallet.ledger.api.dto.OperationStatusResponse;
import br.com.wallet.ledger.api.exceptions.BusinessException;
import br.com.wallet.ledger.api.exceptions.PermanentException;
import br.com.wallet.savings.api.SavingsPlanUseCase;
import br.com.wallet.savings.api.dto.CreateSavingsRuleCommand;
import br.com.wallet.savings.api.model.SavingsRuleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class DownstreamExecutionBridge {

    private static final Logger log = LoggerFactory.getLogger(DownstreamExecutionBridge.class);

    private final TransferFundsUseCase transferFundsUseCase;
    private final OperationQueryUseCase operationQueryUseCase;
    private final SavingsPlanUseCase savingsPlanUseCase;
    private final FinancialGoalUseCase financialGoalUseCase;
    private final ObjectMapper objectMapper;

    public sealed interface ExecutionResult {
        record Success(String executionReference) implements ExecutionResult {}
        record BusinessFailure(String reason) implements ExecutionResult {}
        record TechnicalFailure(Throwable cause) implements ExecutionResult {}
    }

    public enum DownstreamStatus {
        COMPLETED,
        NOT_EXECUTED,
        UNKNOWN
    }

    @Autowired
    public DownstreamExecutionBridge(
            final TransferFundsUseCase transferFundsUseCase,
            final OperationQueryUseCase operationQueryUseCase,
            final SavingsPlanUseCase savingsPlanUseCase,
            final FinancialGoalUseCase financialGoalUseCase) {
        this(transferFundsUseCase, operationQueryUseCase, savingsPlanUseCase, financialGoalUseCase, new ObjectMapper());
    }

    public DownstreamExecutionBridge(
            final TransferFundsUseCase transferFundsUseCase,
            final OperationQueryUseCase operationQueryUseCase,
            final SavingsPlanUseCase savingsPlanUseCase,
            final FinancialGoalUseCase financialGoalUseCase,
            final ObjectMapper objectMapper) {
        this.transferFundsUseCase = transferFundsUseCase;
        this.operationQueryUseCase = operationQueryUseCase;
        this.savingsPlanUseCase = savingsPlanUseCase;
        this.financialGoalUseCase = financialGoalUseCase;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @NonNull
    public ExecutionResult dispatch(@NonNull final FinancialProposal proposal) {
        try {
            return switch (proposal.type()) {
                case TRANSFER -> dispatchTransfer(proposal);
                case SAVINGS_RULE -> dispatchSavingsRule(proposal);
                case GOAL_ADJUSTMENT -> dispatchGoalAdjustment(proposal);
            };
        } catch (Throwable t) {
            if (isBusinessFailure(t)) {
                log.warn("Downstream execution business failure for proposal {}: {}", proposal.id(), t.getMessage());
                return new ExecutionResult.BusinessFailure(t.getMessage());
            } else {
                log.error("Downstream execution technical failure for proposal {}: {}", proposal.id(), t.getMessage(), t);
                return new ExecutionResult.TechnicalFailure(t);
            }
        }
    }

    @NonNull
    public DownstreamStatus checkStatus(@NonNull final ProposalType type, @NonNull final UUID executionOperationId) {
        if (type == ProposalType.TRANSFER) {
            if (operationQueryUseCase == null) {
                return DownstreamStatus.UNKNOWN;
            }
            try {
                Optional<OperationStatusResponse> status = operationQueryUseCase.getOperationStatus(executionOperationId);
                if (status.isPresent() && status.get().status() == OperationStatus.COMPLETED) {
                    return DownstreamStatus.COMPLETED;
                }
                return DownstreamStatus.NOT_EXECUTED;
            } catch (Exception e) {
                log.warn("Failed to check downstream status for {}: {}", executionOperationId, e.getMessage());
                return DownstreamStatus.UNKNOWN;
            }
        }
        return DownstreamStatus.NOT_EXECUTED;
    }

    public void validateParameters(@NonNull final ProposalType type, @NonNull final String parametersJson) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(parametersJson, "parametersJson cannot be null");
        try {
            JsonNode root = objectMapper.readTree(parametersJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("parametersJson must be a valid JSON object");
            }
            switch (type) {
                case TRANSFER -> {
                    parseRequiredUuid(root, "to");
                    parseRequiredDecimal(root, "amount", new BigDecimal("0.01"));
                    if (root.hasNonNull("from")) {
                        parseRequiredUuid(root, "from");
                    }
                }
                case SAVINGS_RULE -> parseSavingsRule(root);
                case GOAL_ADJUSTMENT -> parseGoalAdjustment(root);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed JSON parameters: " + e.getMessage(), e);
        }
    }

    private ExecutionResult dispatchTransfer(FinancialProposal proposal) throws Exception {
        if (transferFundsUseCase == null) {
            throw new IllegalStateException("TransferFundsUseCase is not available");
        }
        JsonNode root = objectMapper.readTree(proposal.parametersJson());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("parametersJson must be a valid JSON object");
        }
        TransferParameters params = parseTransfer(root, proposal);
        transferFundsUseCase.handle(params.transfer());
        return new ExecutionResult.Success(params.executionReference());
    }

    private ExecutionResult dispatchSavingsRule(FinancialProposal proposal) throws Exception {
        if (savingsPlanUseCase == null) {
            throw new IllegalStateException("SavingsPlanUseCase is not available");
        }
        JsonNode root = objectMapper.readTree(proposal.parametersJson());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("parametersJson must be a valid JSON object");
        }
        SavingsRuleParameters params = parseSavingsRule(root);
        savingsPlanUseCase.addRule(params.planId(), params.command());
        return new ExecutionResult.Success(proposal.executionOperationId());
    }

    private ExecutionResult dispatchGoalAdjustment(FinancialProposal proposal) throws Exception {
        if (financialGoalUseCase == null) {
            throw new IllegalStateException("FinancialGoalUseCase is not available");
        }
        JsonNode root = objectMapper.readTree(proposal.parametersJson());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("parametersJson must be a valid JSON object");
        }
        GoalAdjustmentParameters params = parseGoalAdjustment(root);
        financialGoalUseCase.updateGoal(params.goalId(), params.command());
        return new ExecutionResult.Success(proposal.executionOperationId());
    }

    private TransferParameters parseTransfer(JsonNode root, FinancialProposal proposal) {
        UUID to = parseRequiredUuid(root, "to");
        BigDecimal amount = parseRequiredDecimal(root, "amount", new BigDecimal("0.01"));
        UUID from = root.hasNonNull("from") ? parseRequiredUuid(root, "from") : proposal.walletId();
        UUID opId = UUID.fromString(proposal.executionOperationId());

        return new TransferParameters(new Transfer(from, to, amount, opId, OperationOrigin.USER, proposal.tenantId()), opId.toString());
    }

    private SavingsRuleParameters parseSavingsRule(JsonNode root) {
        UUID planId = parseRequiredUuid(root, "planId");
        SavingsRuleType ruleType = parseRequiredEnum(root, "ruleType", SavingsRuleType.class);
        BigDecimal percentage = root.hasNonNull("percentage") ? parseRequiredDecimal(root, "percentage", new BigDecimal("0.01")) : null;
        BigDecimal threshold = root.hasNonNull("threshold") ? parseRequiredDecimal(root, "threshold", new BigDecimal("0.01")) : null;
        BigDecimal step = root.hasNonNull("step") ? parseRequiredDecimal(root, "step", new BigDecimal("0.01")) : null;

        return new SavingsRuleParameters(planId, new CreateSavingsRuleCommand(ruleType, percentage, threshold, step));
    }

    private GoalAdjustmentParameters parseGoalAdjustment(JsonNode root) {
        UUID goalId = parseRequiredUuid(root, "goalId");
        String name = parseRequiredNonBlankString(root, "name");
        BigDecimal targetAmount = parseRequiredDecimal(root, "targetAmount", new BigDecimal("0.01"));
        LocalDate targetDate = parseRequiredFutureDate(root, "targetDate");
        GoalPriority priority = parseRequiredEnum(root, "priority", GoalPriority.class);

        return new GoalAdjustmentParameters(goalId, new UpdateGoalCommand(name, targetAmount, targetDate, priority));
    }

    private UUID parseRequiredUuid(JsonNode root, String fieldName) {
        if (!root.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("Missing required parameter: " + fieldName);
        }
        try {
            return UUID.fromString(root.get(fieldName).asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format for parameter: " + fieldName);
        }
    }

    private String parseRequiredNonBlankString(JsonNode root, String fieldName) {
        if (!root.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("Missing required parameter: " + fieldName);
        }
        String value = root.get(fieldName).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Parameter cannot be blank: " + fieldName);
        }
        return value;
    }

    private BigDecimal parseRequiredDecimal(JsonNode root, String fieldName, BigDecimal minInclusive) {
        if (!root.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("Missing required parameter: " + fieldName);
        }
        try {
            BigDecimal value = new BigDecimal(root.get(fieldName).asText());
            if (minInclusive != null && value.compareTo(minInclusive) < 0) {
                throw new IllegalArgumentException("Parameter " + fieldName + " must be at least " + minInclusive);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid decimal format for parameter: " + fieldName);
        }
    }

    private LocalDate parseRequiredFutureDate(JsonNode root, String fieldName) {
        if (!root.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("Missing required parameter: " + fieldName);
        }
        try {
            LocalDate date = LocalDate.parse(root.get(fieldName).asText());
            if (!date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Parameter " + fieldName + " must be a future date");
            }
            return date;
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format (expected YYYY-MM-DD) for parameter: " + fieldName);
        }
    }

    private <E extends Enum<E>> E parseRequiredEnum(JsonNode root, String fieldName, Class<E> enumClass) {
        if (!root.hasNonNull(fieldName)) {
            throw new IllegalArgumentException("Missing required parameter: " + fieldName);
        }
        String text = root.get(fieldName).asText();
        try {
            return Enum.valueOf(enumClass, text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value '" + text + "' for parameter: " + fieldName);
        }
    }

    private boolean isBusinessFailure(Throwable t) {
        return t instanceof BusinessException || t instanceof PermanentException || t instanceof AccountBlockedException || t instanceof IllegalArgumentException;
    }

    private record GoalAdjustmentParameters(UUID goalId, UpdateGoalCommand command) {}
    private record SavingsRuleParameters(UUID planId, CreateSavingsRuleCommand command) {}
    private record TransferParameters(Transfer transfer, String executionReference) {}
}
