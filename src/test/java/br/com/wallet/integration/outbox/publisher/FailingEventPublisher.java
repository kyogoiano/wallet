package br.com.wallet.integration.outbox.publisher;

import br.com.wallet.infrasctructure.messaging.EventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class FailingEventPublisher implements EventPublisher {

    private volatile boolean fail = false;

    public void enableFailure(boolean value) {
        this.fail = value;
    }

    @Override
    public void publish(String eventType, String payload) {
        throw new RuntimeException("Simulated failure");
    }
}
