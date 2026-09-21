package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.edge.api.CommandType;
import br.com.wallet.edge.api.OperationStatusBroadcaster;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.WithdrawFundsUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.ledger.api.context.Withdraw;
import br.com.wallet.ledger.api.exceptions.ExceptionType;
import br.com.wallet.ledger.api.guard.FraudCheckHelper;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.UUID;

/**
 * Dedicated core consumer processing financial commands from the Edge Gateway (REQ-EDG-022).
 * Subscribes to commands.wallet.* on the commands stream, invokes ledger use cases,
 * pushes real-time status transitions to OperationStatusBroadcaster, and enforces 3-tier failure handling.
 */
@Component
public class CoreCommandConsumer extends AbstractNatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CoreCommandConsumer.class);
    private static final String STREAM_NAME = "commands";
    private static final String DURABLE_CONSUMER_NAME = "core-command-consumer";
    private static final String SUBJECT = "commands.wallet.*";

    private final ObjectMapper objectMapper;
    private final TransferFundsUseCase transferFundsUseCase;
    private final DepositFundsUseCase depositFundsUseCase;
    private final WithdrawFundsUseCase withdrawFundsUseCase;
    private final FraudCheckHelper fraudCheckHelper;
    private final OperationStatusBroadcaster statusBroadcaster;
    private final DlqPublisher dlqPublisher;
    private final OperationStateUseCase operationStateUseCase;

    public CoreCommandConsumer(
            @Autowired final Connection natsConnection,
            @Autowired final ObjectMapper objectMapper,
            @Autowired final TransferFundsUseCase transferFundsUseCase,
            @Autowired final DepositFundsUseCase depositFundsUseCase,
            @Autowired final WithdrawFundsUseCase withdrawFundsUseCase,
            @Autowired final FraudCheckHelper fraudCheckHelper,
            @Autowired final OperationStatusBroadcaster statusBroadcaster,
            @Autowired final DlqPublisher dlqPublisher,
            @Autowired(required = false) final OperationStateUseCase operationStateUseCase
    ) {
        super(SUBJECT, natsConnection);
        this.objectMapper = objectMapper;
        this.transferFundsUseCase = transferFundsUseCase;
        this.depositFundsUseCase = depositFundsUseCase;
        this.withdrawFundsUseCase = withdrawFundsUseCase;
        this.fraudCheckHelper = fraudCheckHelper;
        this.statusBroadcaster = statusBroadcaster;
        this.dlqPublisher = dlqPublisher;
        this.operationStateUseCase = operationStateUseCase;
    }

    @Override
    public void init() throws Exception {
        setupGeneralSubscription(STREAM_NAME, DURABLE_CONSUMER_NAME);
        log.info("CoreCommandConsumer initialized on stream '{}' with subject '{}' and consumer '{}'",
                STREAM_NAME, SUBJECT, DURABLE_CONSUMER_NAME);
    }

    @Override
    public void processMessage(@NonNull final Message message) {
        CommandType type;
        UUID operationId = null;
        String tenantId;

        try {
            operationId = extractOperationId(message);
            type = resolveCommandType(message);
            validateTransportSecurity(message);
            tenantId = extractHeader(message, "tenant_id");
        } catch (SecurityException se) {
            log.error("Rejected command due to security/tenant violation: opId={}, subject={}, error={}",
                    operationId, message.getSubject(), se.getMessage());
            if (operationId != null) {
                statusBroadcaster.publishStatus(operationId, "FAILED", se.getMessage());
                if (operationStateUseCase != null) {
                    String opTenant = extractHeader(message, "tenant_id");
                    operationStateUseCase.markOperationFailed(
                            operationId,
                            se.getMessage(),
                            "FORBIDDEN_TENANT_ACCESS",
                            opTenant != null ? opTenant : "default"
                    );
                }
            }
            message.ack();
            return;
        } catch (Exception e) {
            log.error("Failed to parse command envelope / type from message: subject={}", message.getSubject(), e);
            handlePoisonMessage(message, null, operationId, e);
            return;
        }

        final Object command;
        try {
            JsonNode node = parseJsonNode(message.getData(), operationId, tenantId);
            command = deserializeCommand(node, type);
        } catch (Exception e) {
            log.error("Poison message detected while deserializing command: opId={}, type={}, subject={}",
                    operationId, type, message.getSubject(), e);
            handlePoisonMessage(message, type, operationId, e);
            return;
        }

        final long deliveries = message.metaData() != null ? message.metaData().deliveredCount() : 1;

        try {
            log.info("Processing command [{}] attempt {} for opId={}", type, deliveries, operationId);

            dispatchCommand(type, command);

            // Notify terminal success
            if (operationId != null) {
                statusBroadcaster.publishStatus(operationId, "COMPLETED", "Command executed successfully");
                if (operationStateUseCase != null) {
                    operationStateUseCase.markOperationCompleted(operationId, tenantId);
                }
            }
            message.ack();
            log.info("Successfully executed and ACKed command [{}] opId={}", type, operationId);

        } catch (Exception e) {
            RetryDecision decision = RetryPolicy.decide(deliveries, e);
            log.warn("Command [{}] opId={} failed with {}: {}. Decision={}",
                    type, operationId, e.getClass().getSimpleName(), e.getMessage(), decision);

            switch (decision) {
                case ACK -> {
                    // Business failure: permanent rejection, ACK the message to prevent reprocessing loop
                    if (operationId != null) {
                        statusBroadcaster.publishStatus(operationId, "FAILED", e.getMessage());
                        if (operationStateUseCase != null) {
                            String failureCategory = (e instanceof br.com.wallet.core.exceptions.TenantMismatchException)
                                    ? "FORBIDDEN_TENANT_ACCESS"
                                    : ExceptionType.parseException(e).name();
                            operationStateUseCase.markOperationFailed(
                                    operationId,
                                    e.getMessage(),
                                    failureCategory,
                                    tenantId
                            );
                        }
                    }
                    message.ack();
                }
                case RETRY -> {
                    // Transient failure: notify client retrying and NAK with delay backoff
                    if (operationId != null) {
                        statusBroadcaster.publishStatus(operationId, "PROCESSING", "Transient failure: Retrying... (" + e.getMessage() + ")");
                    }
                    message.nakWithDelay(retryDelay(deliveries));
                }
                case DLQ -> // Retry exhausted: publish to DLQ with confirmed PubAck, then ACK
                        handlePoisonMessage(message, type, operationId, e);
            }
        }
    }

    private void dispatchCommand(CommandType type, Object command) {
        switch (type) {
            case TRANSFER -> {
                Transfer transfer = (Transfer) command;
                fraudCheckHelper.performFraudCheck(transfer);
                transferFundsUseCase.handle(transfer);
            }
            case DEPOSIT -> {
                Deposit deposit = (Deposit) command;
                fraudCheckHelper.performFraudCheck(deposit);
                depositFundsUseCase.handle(deposit);
            }
            case WITHDRAW -> {
                Withdraw withdraw = (Withdraw) command;
                fraudCheckHelper.performFraudCheck(withdraw);
                withdrawFundsUseCase.handle(withdraw);
            }
        }
    }

    private void handlePoisonMessage(Message message, CommandType type, UUID operationId, Exception error) {
        String dlqSubject = resolveDlqSubject(type);
        try {
            log.error("Routing poison message to DLQ subject={}: error={}", dlqSubject, error.getMessage());
            dlqPublisher.publishDlqConfirmed(dlqSubject, natsConnection, message, error);
            if (operationId != null) {
                statusBroadcaster.publishStatus(operationId, "FAILED", "Routed to DLQ: " + error.getMessage());
                if (operationStateUseCase != null) {
                    operationStateUseCase.markOperationFailed(
                            operationId,
                            error.getMessage(),
                            ExceptionType.parseException(error).name()
                    );
                }
            }
            message.ack();
        } catch (Exception dlqEx) {
            log.error("CRITICAL: Failed to publish poison message to DLQ confirmed: {}", dlqEx.getMessage(), dlqEx);
            message.nakWithDelay(Duration.ofSeconds(5));
        }
    }

    private UUID extractOperationId(Message message) {
        if (message.getHeaders() != null) {
            String opIdStr = message.getHeaders().getFirst("operation_id");
            if (opIdStr == null) {
                opIdStr = message.getHeaders().getFirst("Nats-Msg-Id");
            }
            if (opIdStr != null && !opIdStr.isBlank()) {
                return UUID.fromString(opIdStr);
            }
        }
        return null;
    }

    private CommandType resolveCommandType(Message message) {
        if (message.getHeaders() != null) {
            String typeHeader = message.getHeaders().getFirst("type");
            if (typeHeader != null) {
                try {
                    return CommandType.valueOf(typeHeader.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        String subject = message.getSubject();
        if (subject != null) {
            if (subject.endsWith(".transfer") || subject.endsWith("transfer")) {
                return CommandType.TRANSFER;
            } else if (subject.endsWith(".deposit") || subject.endsWith("deposit")) {
                return CommandType.DEPOSIT;
            } else if (subject.endsWith(".withdraw") || subject.endsWith("withdraw")) {
                return CommandType.WITHDRAW;
            }
        }
        throw new IllegalArgumentException("Cannot determine CommandType from subject '" + subject + "' or headers");
    }

    private String resolveDlqSubject(CommandType type) {
        if (type == null) {
            return "commands.dlq.wallet";
        }
        return switch (type) {
            case TRANSFER -> "commands.dlq.transfer";
            case DEPOSIT -> "commands.dlq.deposit";
            case WITHDRAW -> "commands.dlq.withdraw";
        };
    }

    private static final java.util.Set<String> AUTHORIZED_PUBLISHERS = java.util.Set.of("edge-gateway");

    private void validateTransportSecurity(Message message) {
        String tenantId = extractHeader(message, "tenant_id");
        if (tenantId == null) {
            throw new SecurityException("Missing mandatory tenant_id header from NATS publisher");
        }
        String principalId = extractHeader(message, "principal_id");
        String keyId = extractHeader(message, "key_id");
        if (principalId == null || keyId == null) {
            throw new SecurityException("Missing mandatory authentication headers (principal_id/key_id)");
        }
        String publisherId = extractHeader(message, "publisher_id");
        if (publisherId == null || !AUTHORIZED_PUBLISHERS.contains(publisherId)) {
            throw new SecurityException("Unauthorized publisher identity: " + publisherId);
        }
    }

    private String extractHeader(Message message, String name) {
        if (message.getHeaders() != null) {
            String value = message.getHeaders().getFirst(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private JsonNode parseJsonNode(byte[] data, UUID fallbackOpId, String tenantId) {
        JsonNode node = objectMapper.readTree(data);
        while (node != null && node.isString()) {
            try {
                node = objectMapper.readTree(node.asString());
            } catch (Exception e) {
                break;
            }
        }
        if (node instanceof ObjectNode objNode) {
            if (fallbackOpId != null && !objNode.has("operationId")) {
                objNode.put("operationId", fallbackOpId.toString());
            }
            if (tenantId != null) {
                objNode.put("tenantId", tenantId);
            }
            if (objNode.has("sourceAccountId") && !objNode.has("from")) {
                objNode.set("from", objNode.get("sourceAccountId"));
            }
            if (objNode.has("targetAccountId") && !objNode.has("to")) {
                objNode.set("to", objNode.get("targetAccountId"));
            }
            if (objNode.has("accountId") && !objNode.has("walletId")) {
                objNode.set("walletId", objNode.get("accountId"));
            }
        }
        return node;
    }

    private Object deserializeCommand(JsonNode node, CommandType type) {
        return switch (type) {
            case TRANSFER -> objectMapper.treeToValue(node, Transfer.class);
            case DEPOSIT -> objectMapper.treeToValue(node, Deposit.class);
            case WITHDRAW -> objectMapper.treeToValue(node, Withdraw.class);
        };
    }
}
