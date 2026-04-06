package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.application.usecase.TransferFundsUseCase;
import br.com.wallet.domain.context.Transfer;
import br.com.wallet.domain.envelope.CommandEnvelope;
import br.com.wallet.exceptions.BusinessException;
import br.com.wallet.exceptions.TransientException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nats.client.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Consumer bounded to the context of transfer.
 * commands.<bounded-context>.<optionalSubAction>
 * Optional sub Actions make sense when we have more than one action inside the context ( that may represent an action )
 */
@Component
public class TransferCommandConsumer extends AbstractNatsConsumer  {
    private static final String subject = "commands.transfer"; // Subject for transfer commands
    private static final String dlqSubject = "commands.dlq.transfer"; // Subject for dlq transfer commands
    private static final String durableConsumerName = "transfer-consumer"; // Durable consumer name for JetStream
    private static final Logger log = LoggerFactory.getLogger(TransferCommandConsumer.class);
    private static final long maxDeliver = 5; // should match consumer config
    private final Connection natsConnection;
    private final ObjectMapper objectMapper;
    private final TransferFundsUseCase transferUseCase;

    public TransferCommandConsumer(final Connection natsConnection,
                                   final ObjectMapper objectMapper,
                                   final TransferFundsUseCase transferUseCase) {
        this.natsConnection = natsConnection;
        this.objectMapper = objectMapper;
        this.transferUseCase = transferUseCase;
    }

    @Override
    public void init() throws Exception {
        // Start polling for messages in a separate thread (or virtual thread)
        setupGeneralSubscription(subject, durableConsumerName, natsConnection);
    }

    @Override
    void processMessage(@NonNull final Message message) {
        final CommandEnvelope<Transfer> envelope;
        try {
            envelope = objectMapper.readValue(
                    message.getData(),
                    new TypeReference<CommandEnvelope<Transfer>>() {}
            );
        } catch (Exception e) {
            log.error("Invalid payload → DLQ");
            handleDlqMessage(dlqSubject, natsConnection, message, e);
            message.ack();
            return;
        }

        final long deliveries = message.metaData().deliveredCount();
        final var operationId = envelope.operationId();

        try {

            log.info("Processing attempt {} for operationId={}", deliveries, operationId);

            // 💼 Transactional business logic
            transferUseCase.handle(envelope.payload());
            message.ack();

            log.info("Processed operationId={}", operationId);

        } catch (BusinessException e) {
            // ❗ DO NOT retry
            log.warn("Business failure: {}", e.getMessage());
            message.ack();

        } catch (TransientException e) {
            if (deliveries >= maxDeliver) {
                log.error("Max delivery reached for operationId={}, sending to DLQ", operationId);

                handleDlqMessage(dlqSubject, natsConnection, message, e); // DLQ op
                message.ack(); // 🔥 VERY IMPORTANT: stop redelivery
                return;
            }
            // 🔁 retry via JetStream
            log.warn("Transient failure, will retry: {}", e.getMessage());
            message.nakWithDelay(retryDelay(deliveries));

        } catch (Exception e) {
            // 💥 unknown = retry (safe fallback)
            if (deliveries >= maxDeliver) {
                log.error("Unknown failure → DLQ, operationId={}", operationId);
                handleDlqMessage(dlqSubject, natsConnection, message, e);
                message.ack();
                return;
            }

            log.error("Unexpected failure, retrying operationId={}, subject={}",
                    operationId, message.getSubject(), e);
            message.nakWithDelay(retryDelay(deliveries));
        }
    }


}