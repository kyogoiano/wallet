package br.com.wallet.infrasctructure.utils;

import br.com.wallet.domain.event.DomainEventType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class JsonUtils {

    private final ObjectMapper mapper;

    public JsonUtils(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
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
            mapper.readValue(payload, eventType.getClazz());
        } catch (Exception e) {
            throw new RuntimeException("Invalid payload for eventType=" + eventType, e);
        }
    }
}
