package br.com.wallet.infrastructure.messaging.consumer;

import io.nats.client.*;
import io.nats.client.api.*;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generalized nats consumer, this will handle nats consumer setup and polling.
 * Note: Spring will manage all child beans lifecycle.
 */
public abstract class AbstractNatsConsumer implements SmartLifecycle {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    static final long maxDeliver = 5; // should match consumer config

    private ExecutorService executorService;
    private final Semaphore semaphore = new Semaphore(50);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private JetStreamSubscription subscription;
    final String subject;
    final Connection natsConnection;

    public AbstractNatsConsumer(final String subject,
                                final Connection natsConnection) {
        this.subject = subject;
        this.natsConnection = natsConnection;
    }

    protected void setupGeneralSubscription(@NonNull final String streamName,
                                            @NonNull final String consumerName) throws IOException, JetStreamApiException {
        final var jetStream = natsConnection.jetStream();
        final var jsm = natsConnection.jetStreamManagement();

        // Configure consumer for at-least-once delivery, backoff follows current retry strategy
        final var consumerConfig = ConsumerConfiguration.builder()
                .durable(consumerName)
                .filterSubject(subject)
                .ackPolicy(AckPolicy.Explicit)
                .maxDeliver(maxDeliver)
                .backoff(backoffSequence)
                .deliverPolicy(DeliverPolicy.All) // ensure un-acked messages in stream are processed
                .build();

        try {
            final var existing = jsm.getConsumerInfo(streamName, consumerName);
            if (existing != null) {
                final String existingFilter = existing.getConsumerConfiguration().getFilterSubject();
                if (existingFilter != null && !existingFilter.equals(subject)) {
                    log.info("Consumer '{}' filter subject changed from '{}' to '{}'. Recreating consumer...",
                            consumerName, existingFilter, subject);
                    jsm.deleteConsumer(streamName, consumerName);
                }
            }
            jsm.addOrUpdateConsumer(streamName, consumerConfig);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10014 || e.getApiErrorCode() == 404) {
                jsm.addOrUpdateConsumer(streamName, consumerConfig);
            } else {
                try {
                    jsm.deleteConsumer(streamName, consumerName);
                    jsm.addOrUpdateConsumer(streamName, consumerConfig);
                } catch (Exception ex) {
                    log.warn("Could not auto-recreate consumer '{}' via management API: {}", consumerName, ex.getMessage());
                }
            }
        }

        final var pullSubscribeOptions = PullSubscribeOptions.builder()
                .stream(streamName)
                .durable(consumerName)
                .bind(true)
                .build();

        // Subscribe by binding directly to the durable consumer on the stream
        subscription = jetStream.subscribe(null, pullSubscribeOptions);
        log.info("Subscribed to NATS JetStream subject '{}' with durable consumer '{}'.", subject, consumerName);
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

                final Duration timeout = Duration.ofMillis(500);

                final var messages = subscription.fetch(batchSize, timeout);

                if (messages.isEmpty()) {
                    continue; // keep polling
                }
                log.info("Fetched {} messages from NATS JetStream.", messages.size());

                // bulkhead pattern
                for (final Message msg : messages) {
                    semaphore.acquire();
                    try {
                        executorService.submit(() -> {
                            try {
                                this.processMessage(msg);
                            } finally {
                                semaphore.release();
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException e) {
                        semaphore.release();
                        if (running.get()) {
                            throw e;
                        }
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IllegalStateException e) {
                if (!running.get() || (e.getMessage() != null && e.getMessage().contains("inactive"))) {
                    log.debug("NATS subscription closed or became inactive during shutdown: {}", e.getMessage());
                    break;
                }
                log.error("Unexpected IllegalStateException while fetching messages from NATS JetStream: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                if (!running.get() || Thread.currentThread().isInterrupted()) {
                    log.debug("NATS consumer stopping due to shutdown signal: {}", e.getMessage());
                    break;
                }
                log.error("Error fetching messages from NATS JetStream: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000); // Wait before retrying fetch
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public abstract void init() throws Exception;

    abstract void processMessage(@NonNull Message message);

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                init(); // 🔥 ensure subscription exists before polling
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize consumer", e);
            }
            executorService = Executors.newVirtualThreadPerTaskExecutor();
            executorService.submit(this::pollForMessages);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (subscription != null) {
                try {
                    subscription.unsubscribe();
                    log.info("Unsubscribed from NATS JetStream.");
                } catch (Exception e) {
                    log.debug("Subscription already closed during shutdown: {}", e.getMessage());
                }
            }

            if (executorService != null) {
                executorService.shutdownNow(); // immediate stop of polling virtual threads
                log.info("NATS message consumer executor service shut down.");
                try {
                    final var finished = executorService.awaitTermination(5, TimeUnit.SECONDS);
                    log.debug("NATS message consumer executor service shut down finished? : {}", finished);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // start late, stop early
    }

    Duration retryDelay(long deliveries) {
        return switch ((int) deliveries) {
            case 1 -> Duration.ofSeconds(1);
            case 2 -> Duration.ofSeconds(5);
            case 3 -> Duration.ofSeconds(10);
            default -> Duration.ofSeconds(30);
        };
    }

    private static final Duration[] backoffSequence = new Duration[] {
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30)
    };
}
