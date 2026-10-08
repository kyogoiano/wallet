package br.com.wallet.infrasctructure.messaging.consumer;

import br.com.wallet.domain.event.DomainEvent;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public abstract class AbstractEventConsumer<T extends DomainEvent> extends AbstractNatsConsumer {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper objectMapper;

    public AbstractEventConsumer(String subject, Connection natsConnection, ObjectMapper objectMapper) {
        super(subject, natsConnection);
        this.objectMapper = objectMapper;
    }

    @Override
    void processMessage(@NonNull final Message message) {
        try {
            log.debug("Message to be processed from subject: {}, with headers: {}", message.getSubject(), message.getHeaders().toString());
            final T event = objectMapper.readValue(
                    message.getData(),
                    objectMapper.getTypeFactory()
                            .constructParametricType(DomainEvent.class,
                                    Class.forName("br.com.wallet.domain.event." + message.getHeaders().getFirst("type")))
            );

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
