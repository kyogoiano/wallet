package br.com.wallet.infrasctructure.messaging.consumer.business;

import br.com.wallet.application.usecase.CreateWalletUseCase;
import br.com.wallet.domain.context.Wallet;
import br.com.wallet.infrasctructure.messaging.consumer.AbstractNatsConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Consumer bounded to the context of wallet.
 * commands.<bounded-context>.<optionalSubAction>
 * Optional sub Actions make sense when we have more than one action inside the context ( that may represent an action )
 */
@Component
public class CreateWalletCommandConsumer extends AbstractNatsConsumer<Wallet> {
    private static final String streamName = "commands";
    private static final String subject = "commands.wallet"; // Subject for transfer commands
    private static final String dlqSubject = "commands.dlq.wallet"; // Subject for dlq transfer commands
    private static final String durableConsumerName = "wallet-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(CreateWalletCommandConsumer.class);

    public CreateWalletCommandConsumer(@Autowired final Connection natsConnection,
                                       @Autowired final ObjectMapper objectMapper,
                                       @Autowired final CreateWalletUseCase createWalletUseCase) {
        super(subject, dlqSubject, natsConnection, objectMapper, createWalletUseCase);
    }

    @Override
    public void init() throws Exception {
        // Start polling for messages in a separate thread (or virtual thread)
        setupGeneralSubscription(streamName, durableConsumerName);
        log.info("wallet consumer started!");
    }
}