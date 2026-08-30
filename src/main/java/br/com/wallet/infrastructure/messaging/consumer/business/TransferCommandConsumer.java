package br.com.wallet.infrastructure.messaging.consumer.business;

import br.com.wallet.ledger.api.OperationStateUseCase;
import br.com.wallet.ledger.api.TransferFundsUseCase;
import br.com.wallet.ledger.api.context.Transfer;
import br.com.wallet.infrastructure.messaging.consumer.AbstractCommandsConsumer;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import io.nats.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumer bounded to the context of transfer.
 * commands.<bounded-context>.<optionalSubAction>
 * Optional sub Actions make sense when we have more than one action inside the context ( that may represent an action )
 */
@Component
public class TransferCommandConsumer extends AbstractCommandsConsumer<Transfer> {
    private static final String streamName = "commands";
    private static final String subject = "commands.transfer"; // Subject for transfer commands
    private static final String dlqSubject = "commands.dlq.transfer"; // Subject for dlq transfer commands
    private static final String durableConsumerName = "transfer-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(TransferCommandConsumer.class);

    public TransferCommandConsumer(@Autowired final Connection natsConnection,
                                   @Autowired final ObjectMapper objectMapper,
                                   @Autowired final TransferFundsUseCase transferUseCase,
                                   @Autowired final DlqPublisher dlqPublisher,
                                   @Autowired final OperationStateUseCase operationStateUseCase) {
        super(subject, dlqSubject, natsConnection, objectMapper, transferUseCase, dlqPublisher, operationStateUseCase, Transfer.class);
    }

    @Override
    public void init() throws Exception {
        // Start polling for messages in a separate thread (or virtual thread)
        setupGeneralSubscription(streamName, durableConsumerName);
        log.info("transfer consumer started!");
    }
}