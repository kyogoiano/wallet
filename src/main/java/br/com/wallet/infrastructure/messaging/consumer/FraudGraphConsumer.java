package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.fraud.intelligence.projector.RelationalGraphProjector;
import br.com.wallet.ledger.api.AccountUseCase;
import br.com.wallet.ledger.api.event.TransferCompletedEvent;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.wallet.ledger.api.event.DomainEventType;

@Component
public class FraudGraphConsumer extends AbstractEventConsumer<TransferCompletedEvent> {

    private static final Logger log = LoggerFactory.getLogger(FraudGraphConsumer.class);
    private static final String STREAM_NAME = "events";
    private static final String SUBJECT = DomainEventType.TRANSFER_COMPLETED.getSubject();
    private static final String DURABLE_CONSUMER_NAME = "fraud-graph-projector";

    private final RelationalGraphProjector projector;
    private final AccountUseCase accountUseCase;

    public FraudGraphConsumer(
        @Autowired final Connection connection,
        @Autowired final ObjectMapper objectMapper,
        final RelationalGraphProjector projector,
        final AccountUseCase accountUseCase
    ) {
        super(SUBJECT, connection, objectMapper, TransferCompletedEvent.class);
        this.projector = Objects.requireNonNull(projector, "projector cannot be null");
        this.accountUseCase = Objects.requireNonNull(accountUseCase, "accountUseCase cannot be null");
    }

    @Override
    public void init() throws Exception {
        setupGeneralSubscription(STREAM_NAME, DURABLE_CONSUMER_NAME);
        log.info("FraudGraphEventListener subscribed to subject '{}' on stream '{}'", SUBJECT, STREAM_NAME);
    }

    @Override
    void handle(@NonNull final TransferCompletedEvent event) {
        log.info("Processing TransferCompletedEvent in FraudGraphEventListener: opId={}", event.operationId());

        UUID fromUserId;
        try {
            fromUserId = accountUseCase.find(event.from()).userId();
        } catch (Exception e) {
            log.warn("Could not resolve account for wallet {}: {}", event.from(), e.getMessage());
            fromUserId = event.from();
        }

        UUID toUserId;
        try {
            toUserId = accountUseCase.find(event.to()).userId();
        } catch (Exception e) {
            log.warn("Could not resolve account for wallet {}: {}", event.to(), e.getMessage());
            toUserId = event.to();
        }

        projector.projectTransfer(
            event.from(),
            event.to(),
            fromUserId,
            toUserId,
            event.amount(),
            event.operationId(),
            Instant.now()
        );
    }

    @Override
    void handleError(Exception e, Message message) {
        log.error("Failed to project transfer event to graph: {}", e.getMessage(), e);
    }
}
