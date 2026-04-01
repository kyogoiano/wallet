package br.com.wallet.infrasctructure.messaging.consumer;

import io.nats.client.*;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractNatsConsumer {
    private static final Logger log = LoggerFactory.getLogger(AbstractNatsConsumer.class);
    private static final String streamName = "commands";

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore semaphore = new Semaphore(50);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private JetStreamSubscription subscription;


    void setupGeneralSubscription(@NonNull final String subject,
                                  @NonNull final String consumerName,
                                  @NonNull final Connection natsConnection) throws IOException, JetStreamApiException {
        final var jetStream = natsConnection.jetStream();

        // Configure consumer for at-least-once delivery
        final var consumerConfig = ConsumerConfiguration.builder()
                .durable(consumerName)
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(Duration.ofSeconds(30))
                .maxDeliver(5)
                .deliverPolicy(DeliverPolicy.New) // 🔥 important in prod
                .build();

        final var pullOptions = PullSubscribeOptions.builder()
                .stream(streamName)
                .durable(consumerName)
                .configuration(consumerConfig)
                .build();

        // Subscribe to the subject
        subscription = jetStream.subscribe(subject, pullOptions);
        log.info("Subscribed to NATS JetStream subject '{}' with durable consumer '{}'.", subject, consumerName);

        // Start polling for messages in a separate thread (or virtual thread)
        executorService.submit(this::pollForMessages);
    }

    private void pollForMessages() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // Fetch messages from the stream
                final int available = semaphore.availablePermits();
                final int batchSize = Math.min(available, 10);

                if (batchSize == 0) {
                    Thread.sleep(10); // small backoff
                    continue;
                }

                final Duration timeout = Duration.ofSeconds(5);

                final var messages = subscription.fetch(batchSize, timeout);

                if (messages.isEmpty()) {
                    continue; // keep polling
                }
                log.debug("Fetched {} messages from NATS JetStream.", messages.size());

                // bulkhead pattern
                for (final Message msg : messages) {
                    semaphore.acquire();

                    executorService.submit(() -> {
                        try {
                            processMessage(msg);
                        } finally {
                            semaphore.release();
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Error fetching messages from NATS JetStream: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000); // Wait before retrying fetch
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    abstract void processMessage(Message message);

    @PreDestroy
    public void cleanup() {
        running.set(false); // 1. tell loop to stop
        if (subscription != null) {
            subscription.unsubscribe();
            log.info("Unsubscribed from NATS JetStream.");
        }
        executorService.shutdownNow(); // immediate stop due to virtual threads
        log.info("NATS message consumer executor service shut down.");
    }
}
