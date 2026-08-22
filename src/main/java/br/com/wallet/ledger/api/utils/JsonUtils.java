package br.com.wallet.ledger.api.utils;

import br.com.wallet.ledger.api.event.DomainEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

@Component
public final class JsonUtils {

    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
    private final ObjectMapper mapper;
    private final ObjectWriter objectWriter;

    public JsonUtils(final ObjectMapper mapper) {
        this.mapper = mapper;
        objectWriter = mapper.writerFor(this.mapper.constructType(Object.class));
    }

    public String toJson(Object obj) {
        try {
            return objectWriter.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    public void parseDomainEventPayload(
            DomainEventType eventType,
            String payload
    ) {
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown event type!");
        }

        try {
            var domainEvent = mapper.readValue(payload, eventType.getClazz());
            log.info("Domain Event parsed! type={}, parsed={}", eventType, domainEvent.toString());
        } catch (Exception e) {
            throw new RuntimeException("Invalid payload for eventType=" + eventType, e);
        }
    }
}
