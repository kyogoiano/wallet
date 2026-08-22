package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.ledger.api.event.FraudEvent;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;


/**
 * “AntiFraud System Short-term memory”
 */
@Component
public class FraudConsumer extends AbstractEventConsumer<FraudEvent>{
    private static final String streamName = "events";
    private static final String subject = "events.fraud";
    private static final String durableConsumerName = "fraud-consumer";
    private static final Logger log = LoggerFactory.getLogger(FraudConsumer.class);

    private final RedisAsyncCommands<String, String> commands;
    private final FraudProjectionEnricher fraudStateProjectionService;

    public FraudConsumer(@Autowired final Connection connection,
                         @Autowired final ObjectMapper objectMapper,
                         final RedisAsyncCommands<String, String> commands,
                         final FraudProjectionEnricher fraudStateProjectionService) {
        super(subject, connection, objectMapper);
        this.commands = commands;
        this.fraudStateProjectionService = fraudStateProjectionService;
    }


    @Override
    public void init() throws Exception {
        setupGeneralSubscription(streamName, durableConsumerName);
        log.debug("Fraud consumer started!");
    }


    /**
     * this handles fraud event processing and implements closed-loop behavioral feedback system for antifraud concept!
     * In case event is allowed we hit the learning path:
     * timeline + feature store preparation:
     *  - average ticket?
     *  - unique recipients quantity?
     *  - transactions quantity on REVIEW ?
     *  - last transaction blocked?
     *  - time windows usage?
     * TODO: feature store usage and reputation layer
     *
     * @param event fraud event
     */
    @Override
    void handle(@NonNull final FraudEvent event) {

        log.info("Processing fraud event: operationId={}, decision={}",
                event.operationId(), event.decision());

        switch (event.decision()) {
            case REVIEW -> fraudStateProjectionService.processReviewEvent(event);

            case BLOCK -> fraudStateProjectionService.processBlockEvent(event);

            case ALLOW -> commands.zadd(
                    "user:" + event.from() + ":tx_timeline",
                    event.timestamp().toEpochMilli(),
                    event.operationId()
            );    // learning: behavioral memory enrichment

            default -> {
                commands.hset(
                    "tx:" + event.operationId(),
                    Map.of(
                            "amount", event.amount().toString(),
                            "recipient", event.to().toString(),
                            "decision", event.decision().name(),
                            "risk", String.valueOf(event.riskScore())
                    )
                );

                commands.expire(
                        "tx:" + event.operationId(),
                        86400 * 30
                );
            }
        }
    }

    @Override
    void handleError(@NonNull final Exception e, @NonNull final Message message) {
        final long deliveries = message.metaData().deliveredCount();
        message.nakWithDelay(retryDelay(deliveries));
    }



}
