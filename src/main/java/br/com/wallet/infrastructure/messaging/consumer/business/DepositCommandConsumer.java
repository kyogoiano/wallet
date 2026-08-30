package br.com.wallet.infrastructure.messaging.consumer.business;

import br.com.wallet.ledger.api.DepositFundsUseCase;
import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.context.Deposit;
import br.com.wallet.infrastructure.messaging.consumer.AbstractCommandsConsumer;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumer bounded to the context of deposit.
 * commands.<bounded-context>.<optionalSubAction>
 * Optional sub Actions make sense when we have more than one action inside the context ( that may represent an action )
 */
@Component
public class DepositCommandConsumer extends AbstractCommandsConsumer<Deposit> {
    private static final String streamName = "commands";
    private static final String subject = "commands.deposit"; // Subject for transfer commands
    private static final String dlqSubject = "commands.dlq.deposit"; // Subject for dlq transfer commands
    private static final String durableConsumerName = "deposit-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(DepositCommandConsumer.class);

    public DepositCommandConsumer(@Autowired final Connection natsConnection,
                                  @Autowired final ObjectMapper objectMapper,
                                  @Autowired final DepositFundsUseCase depositFundsUseCase,
                                  @Autowired final DlqPublisher dlqPublisher,
                                  @Autowired final OperationStateUseCase operationStateUseCase) {
        super(subject, dlqSubject, natsConnection, objectMapper, depositFundsUseCase, dlqPublisher, operationStateUseCase, Deposit.class);
    }

    @Override
    public void init() throws Exception {
        // Start polling for messages in a separate thread (or virtual thread)
        setupGeneralSubscription(streamName, durableConsumerName);
        log.info("deposit consumer started!");
    }
}