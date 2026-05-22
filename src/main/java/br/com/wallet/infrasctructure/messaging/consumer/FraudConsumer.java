package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.domain.event.FraudEvent;
import br.com.wallet.application.fraud.FraudStateService;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;


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
    private final FraudStateService fraudStateService;

    public FraudConsumer(@Autowired final Connection connection,
                         @Autowired final ObjectMapper objectMapper,
                         final RedisAsyncCommands<String, String> commands,
                         final FraudStateService fraudStateService) {
        super(subject, connection, objectMapper);
        this.commands = commands;
        this.fraudStateService = fraudStateService;
    }


    @Override
    public void init() throws Exception {
        setupGeneralSubscription(streamName, durableConsumerName);
        log.debug("Fraud consumer started!");
    }


    /**
     * this handles fraud event processing and implements closed-loop antifraud concept!
     * @param event fraud event
     */
    @Override
    void handle(@NonNull final FraudEvent event) {

        log.info("Processing fraud event: operationId={}, decision={}",
                event.operationId(), event.decision());

        switch (event.decision()) {
            case REVIEW -> fraudStateService.processReviewEvent(event);

            case BLOCK -> fraudStateService.processBlockEvent(event);

            case ALLOW -> commands.zadd(
                    "user:" + event.from() + ":tx_timeline",
                    event.timestamp().toEpochMilli(),
                    event.operationId()
            );    // 🧠 learning
        }

    }

    @Override
    void handleError(@NonNull final Exception e, @NonNull final Message message) {
        final long deliveries = message.metaData().deliveredCount();
        message.nakWithDelay(retryDelay(deliveries));
    }



}
