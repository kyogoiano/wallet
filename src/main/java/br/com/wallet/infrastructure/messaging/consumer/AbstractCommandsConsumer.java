package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.ledger.api.UseCase;
import br.com.wallet.core.tracing.TraceContext;
import br.com.wallet.ledger.api.envelope.CommandEnvelope;
import br.com.wallet.infrastructure.messaging.publisher.DlqPublisher;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * This will be responsible for all commands message processing
 * * By default all commands messages carries on a Trace context, so this will improve traceability and ease generic behaviors
 * @param <T> trace context of the message
 */
public abstract class AbstractCommandsConsumer <T extends TraceContext> extends AbstractNatsConsumer{
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ObjectMapper objectMapper;
    private final UseCase<T> useCase;
    private final DlqPublisher dlqPublisher;
    private final String dlqSubject;
    private final Class<T> commandClass;

    public AbstractCommandsConsumer(String subject, String dlqSubject, Connection natsConnection, ObjectMapper objectMapper, UseCase<T> useCase, DlqPublisher dlqPublisher, Class<T> commandClass) {
        super(subject, natsConnection);
        this.objectMapper = objectMapper;
        this.useCase = useCase;
        this.dlqPublisher = dlqPublisher;
        this.dlqSubject = dlqSubject;
        this.commandClass = commandClass;
    }

    void processMessage(@NonNull final Message message) {
        final T command;
        try {
            log.debug("Message to be processed from subject: {}, with headers: {}", message.getSubject(), message.getHeaders().toString());
            command = objectMapper.readValue(message.getData(), commandClass);
        } catch (Exception e) {
            log.error("Poison message detected: subject={}", message.getSubject(), e);
            dlqPublisher.handleDlqMessage(dlqSubject, natsConnection, message, e);
            message.ack();
            return;
        }

        final long deliveries = message.metaData().deliveredCount();
        final var operationId = command.operationId();

        try {
            log.info("Processing attempt {} for operationId={}", deliveries, operationId);

            // 💼 Transactional business logic
            useCase.handle(command);
            message.ack();

            log.info("event=processed operationId={} subject={} deliveries={}",
                    operationId, subject, deliveries);

        } catch (Exception e) {
            var retryDecision = RetryPolicy.decide(deliveries, e);
            switch (retryDecision) {
                case DLQ -> {
                    log.error("Max delivery reached for operationId={}, sending to DLQ", operationId);
                    dlqPublisher.handleDlqMessage(dlqSubject, natsConnection, message, e);
                    message.ack();
                    return;
                }
                case ACK -> {
                    log.error("Permanent/Business error for operationId={}, finishing with ACK", operationId);
                    message.ack();
                    return;
                }
            }

            // 🔁 retry via JetStream
            log.warn("Transient failure, will retry: {}", e.getMessage());
            message.nakWithDelay(retryDelay(deliveries));

        }
    }

    protected void beforeHandle(CommandEnvelope<T> envelope, Message message) {}

    protected void afterHandle(CommandEnvelope<T> envelope, Message message) {}

    protected void onFailure(Exception e, CommandEnvelope<T> envelope, Message message) {}


}
