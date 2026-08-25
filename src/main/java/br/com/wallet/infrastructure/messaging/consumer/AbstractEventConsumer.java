package br.com.wallet.infrastructure.messaging.consumer;

import br.com.wallet.ledger.api.event.DomainEvent;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public abstract class AbstractEventConsumer<T extends DomainEvent> extends AbstractNatsConsumer {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper objectMapper;
    private final Class<T> eventClass;

    public AbstractEventConsumer(String subject, Connection natsConnection, ObjectMapper objectMapper, Class<T> eventClass) {
        super(subject, natsConnection);
        this.objectMapper = objectMapper;
        this.eventClass = eventClass;
    }

    @Override
    void processMessage(@NonNull final Message message) {
        try {
            log.debug("Message to be processed from subject: {}, with headers: {}", message.getSubject(), message.getHeaders().toString());

            final T event = objectMapper.readValue(message.getData(), eventClass);

            handle(event);
            message.ack();
        } catch (Exception e) {
            log.error("Error processing event", e);
            handleError(e, message);
        }
    }

    abstract void handle(T event);

    abstract void handleError(Exception e, Message message);
}
